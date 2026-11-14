package com.msa.order.domain.image.entity;

import jakarta.persistence.*;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/** 주문 문의 첨부 메타데이터입니다. 비공개 객체 키만 보관하고 서명 URL은 저장하지 않습니다. */
@Entity
@Table(
        name = "inquiry_images",
        schema = "orders",
        indexes = {
            // 문의 상세의 첨부 목록입니다.
            @Index(name = "idx_inquiry_image_inquiry", columnList = "inquiryId"),
            // 업로드 직후 주문별 대기 이미지 개수를 셉니다.
            @Index(name = "idx_inquiry_image_order", columnList = "orderId"),
            // 정리 작업이 만료된 미정리 이미지를 만료 순으로 찾습니다.
            @Index(name = "idx_inquiry_image_cleanup", columnList = "cleaned,expiresAt")
        })
public class InquiryImage {
    /** 빌더에 담긴 개인정보·결제키·토큰·이벤트 본문을 로그에 노출하지 않습니다. */
    public static class InquiryImageBuilder {
        @Override
        public String toString() {
            return "InquiryImageBuilder[REDACTED]";
        }
    }

    @Id public UUID id;
    @Version public long version;

    @Column(nullable = false)
    public UUID orderId;

    @Column(nullable = false)
    public UUID buyerId;

    public UUID inquiryId;

    @Column(nullable = false)
    public String contentType;

    public long sizeBytes;

    @Column(nullable = false)
    public Instant expiresAt;

    @Column(nullable = false, length = 1000)
    public String uploadKey;

    @Column(nullable = false, length = 1000)
    public String objectKey;

    public boolean verified;
    public boolean cleaned;
    public boolean discarded;

    protected InquiryImage() {}

    // 기존 생성자를 거쳐 UUID·초기 상태·시간과 컬렉션 초기화를 보존하며 JPA 기본 생성자도 유지합니다.
    @Builder
    public InquiryImage(UUID order, UUID buyer, String type, long size, Instant expires) {
        id = UUID.randomUUID();
        orderId = order;
        buyerId = buyer;
        contentType = type;
        sizeBytes = size;
        expiresAt = expires;
        uploadKey = "order-inquiry-uploads/" + order + "/" + id;
        objectKey = "order-inquiry-images/" + order + "/" + id;
    }
}
