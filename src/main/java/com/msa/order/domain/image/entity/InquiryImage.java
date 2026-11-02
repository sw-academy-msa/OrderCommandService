package com.msa.order.domain.image.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** 주문 문의 첨부 메타데이터입니다. 비공개 객체 키만 보관하고 서명 URL은 저장하지 않습니다. */
@Entity
@Table(name = "inquiry_images", schema = "orders")
public class InquiryImage {
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
