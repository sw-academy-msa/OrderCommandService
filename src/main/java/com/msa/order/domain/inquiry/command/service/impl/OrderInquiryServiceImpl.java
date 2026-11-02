package com.msa.order.domain.inquiry.command.service.impl;

import com.msa.order.domain.image.repository.InquiryImageRepository;
import com.msa.order.domain.inquiry.command.dto.request.InquiryRequests;
import com.msa.order.domain.inquiry.command.dto.response.InquiryResponse;
import com.msa.order.domain.inquiry.command.service.OrderInquiryService;
import com.msa.order.domain.inquiry.entity.OrderInquiry;
import com.msa.order.domain.inquiry.repository.OrderInquiryRepository;
import com.msa.order.domain.order.OrderError;
import com.msa.order.domain.order.entity.PurchaseOrder;
import com.msa.order.domain.order.repository.OrderRepository;
import com.msa.order.domain.outbox.entity.OutboxEvent;
import com.msa.order.global.error.BusinessException;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.*;

/** 주문 잠금 → 이미지 순서로 처리하고 문의/Outbox를 함께 저장합니다. FAQ로 자동 공개하지 않습니다. */
@Service
@Transactional
public class OrderInquiryServiceImpl implements OrderInquiryService {
    private final OrderRepository orders;
    private final OrderInquiryRepository inquiries;
    private final InquiryImageRepository images;
    private final EntityManager em;
    private final JsonMapper json;
    private final Clock clock;

    public OrderInquiryServiceImpl(
            OrderRepository orders,
            OrderInquiryRepository inquiries,
            InquiryImageRepository images,
            EntityManager em,
            JsonMapper json,
            Clock clock) {
        this.orders = orders;
        this.inquiries = inquiries;
        this.images = images;
        this.em = em;
        this.json = json;
        this.clock = clock;
    }

    public InquiryResponse create(UUID buyer, UUID order, InquiryRequests.Create r) {
        var o = owned(buyer, order, false);
        if (o.state != PurchaseOrder.State.CONFIRMED
                || new HashSet<>(r.imageIds()).size() != r.imageIds().size()) {
            fail(OrderError.STATE);
        }
        UUID id =
                UUID.nameUUIDFromBytes(
                        (order + ":" + r.requestKey()).getBytes(StandardCharsets.UTF_8));
        String hash =
                hash(
                        r.category()
                                + "\n"
                                + r.body()
                                + "\n"
                                + r.imageIds().stream().sorted().toList());
        var old = inquiries.findById(id);
        if (old.isPresent()) {
            if (!old.get().requestHash.equals(hash)) {
                fail(OrderError.DUPLICATE);
            }
            return response(old.get());
        }
        var i =
                new OrderInquiry(
                        id, order, buyer, o.sellerId, r.category().trim(), hash, clock.instant());
        i.messages.add(
                new OrderInquiry.Message(r.requestKey(), false, r.body().trim(), clock.instant()));
        inquiries.saveAndFlush(i);
        r.imageIds().stream()
                .sorted()
                .forEach(
                        imageId -> {
                            var image =
                                    images.lock(imageId)
                                            .orElseThrow(
                                                    () ->
                                                            new BusinessException(
                                                                    OrderError.NOT_FOUND));
                            if (!buyer.equals(image.buyerId)
                                    || !order.equals(image.orderId)
                                    || !image.verified
                                    || image.inquiryId != null
                                    || image.cleaned
                                    || image.discarded) {
                                fail(OrderError.OWNER);
                            }
                            image.inquiryId = id;
                        });
        emit(i);
        return response(i);
    }

    public InquiryResponse message(
            UUID actor, UUID order, UUID id, boolean seller, InquiryRequests.Message r) {
        owned(actor, order, seller);
        var i = inquiry(order, id);
        var old = i.messages.stream().filter(m -> m.requestKey.equals(r.requestKey())).findFirst();
        if (old.isPresent()) {
            if (old.get().seller != seller || !old.get().body.equals(r.body().trim())) {
                fail(OrderError.DUPLICATE);
            }
            return response(i);
        }
        i.messages.add(
                new OrderInquiry.Message(r.requestKey(), seller, r.body().trim(), clock.instant()));
        inquiries.flush();
        emit(i);
        return response(i);
    }

    public InquiryResponse get(UUID actor, UUID order, UUID id, boolean seller) {
        owned(actor, order, seller);
        return response(inquiry(order, id));
    }

    private PurchaseOrder owned(UUID actor, UUID order, boolean seller) {
        var o = orders.lock(order).orElseThrow(() -> new BusinessException(OrderError.NOT_FOUND));
        if (!actor.equals(seller ? o.sellerId : o.buyerId)) {
            fail(OrderError.OWNER);
        }
        return o;
    }

    private OrderInquiry inquiry(UUID order, UUID id) {
        var i =
                inquiries
                        .findById(id)
                        .orElseThrow(() -> new BusinessException(OrderError.NOT_FOUND));
        if (!order.equals(i.orderId)) {
            fail(OrderError.NOT_FOUND);
        }
        return i;
    }

    private InquiryResponse response(OrderInquiry i) {
        return InquiryResponse.from(
                i, images.findByInquiryId(i.id).stream().map(v -> v.id).toList());
    }

    private void emit(OrderInquiry i) {
        UUID event = UUID.randomUUID();
        var attachments =
                images.findByInquiryId(i.id).stream()
                        .map(
                                v ->
                                        Map.of(
                                                "id",
                                                v.id,
                                                "objectKey",
                                                v.objectKey,
                                                "contentType",
                                                v.contentType,
                                                "sizeBytes",
                                                v.sizeBytes))
                        .toList();
        var snapshot =
                Map.of(
                        "id",
                        i.id,
                        "orderId",
                        i.orderId,
                        "buyerId",
                        i.buyerId,
                        "sellerId",
                        i.sellerId,
                        "category",
                        i.category,
                        "messages",
                        i.messages,
                        "images",
                        attachments,
                        "createdAt",
                        i.createdAt);
        var envelope =
                Map.of(
                        "eventId",
                        event,
                        "schemaVersion",
                        1,
                        "type",
                        "ORDER_INQUIRY_UPSERT",
                        "aggregateId",
                        i.id,
                        "aggregateVersion",
                        i.messages.size(),
                        "occurredAt",
                        clock.instant(),
                        "inquiry",
                        snapshot);
        em.persist(
                new OutboxEvent(
                        event,
                        "stockdrop.orders.inquiries.private.v1",
                        i.orderId,
                        json.writeValueAsString(envelope),
                        clock.instant()));
    }

    private String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 미지원");
        }
    }

    private void fail(OrderError code) {
        throw new BusinessException(code);
    }
}
