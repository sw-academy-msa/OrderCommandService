package com.msa.order.domain.image.command.service.impl;

import com.msa.order.domain.image.command.dto.request.ImageRequest;
import com.msa.order.domain.image.command.dto.response.ImageReply;
import com.msa.order.domain.image.command.service.InquiryImageService;
import com.msa.order.domain.image.entity.InquiryImage;
import com.msa.order.domain.image.repository.InquiryImageRepository;
import com.msa.order.domain.order.OrderError;
import com.msa.order.domain.order.entity.PurchaseOrder;
import com.msa.order.domain.order.repository.OrderRepository;
import com.msa.order.global.config.storage.ImageStorageProperties;
import com.msa.order.global.error.BusinessException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.*;

import java.time.*;
import java.util.*;

/** S3와 DB는 분산 트랜잭션이 아닙니다. 발급 이력을 먼저 남기고 실패한 미연결 객체는 재시도 정리합니다. */
@Service
@Transactional(timeout = 15)
public class InquiryImageServiceImpl implements InquiryImageService {
    private final OrderRepository orders;
    private final InquiryImageRepository images;
    private final ImageStorageProperties properties;
    private final S3Client s3;
    private final S3Presigner presigner;
    private final Clock clock;

    public InquiryImageServiceImpl(
            OrderRepository orders,
            InquiryImageRepository images,
            ImageStorageProperties properties,
            S3Client s3,
            S3Presigner presigner,
            Clock clock) {
        this.orders = orders;
        this.images = images;
        this.properties = properties;
        this.s3 = s3;
        this.presigner = presigner;
        this.clock = clock;
    }

    public ImageReply issue(UUID buyer, UUID order, ImageRequest r) {
        var o = orders.lock(order).orElseThrow(() -> new BusinessException(OrderError.NOT_FOUND));
        if (!buyer.equals(o.buyerId)) {
            fail(OrderError.OWNER);
        }
        if (o.state != PurchaseOrder.State.CONFIRMED) {
            fail(OrderError.STATE);
        }
        ready();
        if (!properties.allowedTypes().contains(r.contentType())
                || !Set.of("image/png", "image/jpeg", "image/webp").contains(r.contentType())
                || r.sizeBytes() <= 0
                || r.sizeBytes() > properties.maxBytes()) {
            fail(OrderError.INVALID);
        }
        // 미사용 업로드 권한은 한 번에 최대 5장입니다. 연결 후에는 다음 문의의 사진을 추가 발급할 수 있습니다.
        if (images.countPending(order, clock.instant()) >= 5) {
            fail(OrderError.STATE);
        }
        var image =
                images.save(
                        new InquiryImage(
                                order,
                                buyer,
                                r.contentType(),
                                r.sizeBytes(),
                                clock.instant().plusSeconds(300)));
        var put =
                PutObjectRequest.builder()
                        .bucket(properties.bucket())
                        .key(image.uploadKey)
                        .contentType(image.contentType)
                        .contentLength(image.sizeBytes)
                        .build();
        String url =
                presigner
                        .presignPutObject(
                                PutObjectPresignRequest.builder()
                                        .signatureDuration(Duration.ofMinutes(5))
                                        .putObjectRequest(put)
                                        .build())
                        .url()
                        .toExternalForm();
        return new ImageReply(image.id, url, image.contentType, image.sizeBytes, image.expiresAt);
    }

    public ImageReply complete(UUID buyer, UUID order, UUID id) {
        var image = owned(buyer, order, id, false);
        if (image.discarded) {
            fail(OrderError.STATE);
        }
        if (image.verified) {
            return new ImageReply(id, null, image.contentType, image.sizeBytes, image.expiresAt);
        }
        ready();
        if (!clock.instant().isBefore(image.expiresAt) || image.cleaned) {
            fail(OrderError.STATE);
        }
        try {
            var head =
                    s3.headObject(
                            HeadObjectRequest.builder()
                                    .bucket(properties.bucket())
                                    .key(image.uploadKey)
                                    .build());
            if (head.contentLength() != image.sizeBytes
                    || !image.contentType.equals(head.contentType())) {
                fail(OrderError.INVALID);
            }
            byte[] bytes =
                    s3.getObjectAsBytes(
                                    GetObjectRequest.builder()
                                            .bucket(properties.bucket())
                                            .key(image.uploadKey)
                                            .range("bytes=0-15")
                                            .ifMatch(head.eTag())
                                            .build())
                            .asByteArray();
            if (!matches(image.contentType, bytes)) {
                fail(OrderError.INVALID);
            }
            // 같은 이미지 완료 작업은 행 잠금으로 직렬화해 재업로드가 최종 첨부를 덮어쓰지 못하게 합니다.
            s3.copyObject(
                    CopyObjectRequest.builder()
                            .sourceBucket(properties.bucket())
                            .sourceKey(image.uploadKey)
                            .destinationBucket(properties.bucket())
                            .destinationKey(image.objectKey)
                            .copySourceIfMatch(head.eTag())
                            .build());
            image.verified = true;
            return new ImageReply(id, null, image.contentType, image.sizeBytes, image.expiresAt);
        } catch (S3Exception ex) {
            throw new BusinessException(
                    ex.statusCode() == 404 || ex.statusCode() == 412
                            ? OrderError.INVALID
                            : OrderError.UNAVAILABLE);
        }
    }

    public ImageReply download(UUID actor, UUID order, UUID id, boolean seller) {
        var image = owned(actor, order, id, seller);
        ready();
        if (!image.verified || image.discarded || seller && image.inquiryId == null) {
            fail(OrderError.NOT_FOUND);
        }
        var get =
                GetObjectRequest.builder().bucket(properties.bucket()).key(image.objectKey).build();
        String url =
                presigner
                        .presignGetObject(
                                GetObjectPresignRequest.builder()
                                        .signatureDuration(Duration.ofMinutes(5))
                                        .getObjectRequest(get)
                                        .build())
                        .url()
                        .toExternalForm();
        return new ImageReply(
                id, url, image.contentType, image.sizeBytes, clock.instant().plusSeconds(300));
    }

    public void cleanup(UUID id) {
        var image = images.lock(id).orElse(null);
        if (image == null
                || image.cleaned
                || !image.expiresAt.isBefore(clock.instant().minus(Duration.ofHours(24)))) {
            return;
        }
        ready();
        s3.deleteObject(
                DeleteObjectRequest.builder()
                        .bucket(properties.bucket())
                        .key(image.uploadKey)
                        .build());
        if (image.inquiryId == null) {
            s3.deleteObject(
                    DeleteObjectRequest.builder()
                            .bucket(properties.bucket())
                            .key(image.objectKey)
                            .build());
        }
        image.cleaned = true;
    }

    public void discard(UUID buyer, UUID order, UUID id) {
        var image = owned(buyer, order, id, false);
        if (image.inquiryId != null) {
            fail(OrderError.STATE);
        }
        // 발급 URL이 만료된 뒤 정리해야 삭제 직후 재업로드한 객체도 놓치지 않습니다.
        image.discarded = true;
    }

    private InquiryImage owned(UUID actor, UUID order, UUID id, boolean seller) {
        var o =
                orders.findById(order)
                        .orElseThrow(() -> new BusinessException(OrderError.NOT_FOUND));
        if (!actor.equals(seller ? o.sellerId : o.buyerId)) {
            fail(OrderError.OWNER);
        }
        var image = images.lock(id).orElseThrow(() -> new BusinessException(OrderError.NOT_FOUND));
        if (!order.equals(image.orderId) || !o.buyerId.equals(image.buyerId)) {
            fail(OrderError.OWNER);
        }
        return image;
    }

    private void ready() {
        if (!properties.ready()) {
            fail(OrderError.UNAVAILABLE);
        }
    }

    private void fail(OrderError error) {
        throw new BusinessException(error);
    }

    static boolean matches(String type, byte[] b) {
        return switch (type) {
            case "image/png" ->
                    b.length >= 8
                            && Arrays.equals(
                                    Arrays.copyOf(b, 8),
                                    new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10});
            case "image/jpeg" ->
                    b.length >= 3
                            && (b[0] & 255) == 255
                            && (b[1] & 255) == 216
                            && (b[2] & 255) == 255;
            case "image/webp" ->
                    b.length >= 12
                            && new String(b, 0, 4, java.nio.charset.StandardCharsets.US_ASCII)
                                    .equals("RIFF")
                            && new String(b, 8, 4, java.nio.charset.StandardCharsets.US_ASCII)
                                    .equals("WEBP");
            default -> false;
        };
    }
}
