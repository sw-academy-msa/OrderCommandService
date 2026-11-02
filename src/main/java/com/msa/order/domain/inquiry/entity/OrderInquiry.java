package com.msa.order.domain.inquiry.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.*;

/** 특정 주문의 비공개 판매자 문의입니다. 상품 질문/공개 FAQ는 Product 책임으로 유지합니다. */
@Entity
@Table(name = "order_inquiries", schema = "orders")
public class OrderInquiry {
    @Id public UUID id;
    @Version public long version;

    @Column(nullable = false)
    public UUID orderId;

    @Column(nullable = false)
    public UUID buyerId;

    @Column(nullable = false)
    public UUID sellerId;

    @Column(nullable = false, length = 100)
    public String category;

    @Column(nullable = false, length = 64)
    public String requestHash;

    public Instant createdAt;

    @ElementCollection
    @CollectionTable(
            name = "order_inquiry_messages",
            schema = "orders",
            joinColumns = @JoinColumn(name = "inquiry_id"))
    @OrderColumn(name = "message_position")
    public List<Message> messages = new ArrayList<>();

    protected OrderInquiry() {}

    public OrderInquiry(
            UUID id,
            UUID order,
            UUID buyer,
            UUID seller,
            String category,
            String hash,
            Instant now) {
        this.id = id;
        orderId = order;
        buyerId = buyer;
        sellerId = seller;
        this.category = category;
        requestHash = hash;
        createdAt = now;
    }

    @Embeddable
    public static class Message {
        @Column(nullable = false)
        public UUID requestKey;

        @Column(nullable = false)
        public boolean seller;

        @Column(nullable = false, length = 1000)
        public String body;

        public Instant createdAt;

        protected Message() {}

        public Message(UUID key, boolean seller, String body, Instant now) {
            requestKey = key;
            this.seller = seller;
            this.body = body;
            createdAt = now;
        }

        @Override
        public String toString() {
            return "Message[REDACTED]";
        }
    }
}
