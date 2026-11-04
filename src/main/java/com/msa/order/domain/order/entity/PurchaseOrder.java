package com.msa.order.domain.order.entity;

import jakarta.persistence.*;

import lombok.Builder;

import java.time.Instant;
import java.util.*;

/** orders 원본입니다. 외부 ID는 값만 저장하고 서비스 간 FK·JOIN·공유 트랜잭션을 금지합니다. */
@Entity
@Table(
        name = "purchase_orders",
        schema = "orders",
        uniqueConstraints = @UniqueConstraint(columnNames = {"buyer_id", "request_key"}),
        indexes = @Index(name = "idx_order_saga_due", columnList = "nextAttemptAt,leaseUntil"))
public class PurchaseOrder {
    /** 빌더에 담긴 개인정보·결제키·토큰·이벤트 본문을 로그에 노출하지 않습니다. */
    public static class PurchaseOrderBuilder {
        @Override
        public String toString() {
            return "PurchaseOrderBuilder[REDACTED]";
        }
    }

    public enum State {
        RESERVING,
        HELD,
        STARTING_PAYMENT,
        APPROVING_PAYMENT,
        VERIFYING_PAYMENT,
        CONFIRMING_STOCK,
        COMPENSATING_PAYMENT,
        RELEASING_STOCK,
        CONFIRMED,
        FAILED,
        EXPIRED
    }

    public enum Delivery {
        PREPARING,
        SHIPPING,
        DELIVERED
    }

    @Id public UUID id;
    @Version public long version;
    public long projectionVersion;

    @Column(nullable = false, updatable = false)
    public UUID buyerId;

    @Column(nullable = false, updatable = false)
    public UUID requestKey;

    @Column(nullable = false, updatable = false, length = 64)
    public String requestHash;

    @Column(nullable = false, updatable = false)
    public UUID productId;

    public UUID sellerId;

    @Column(length = 200)
    public String productName;

    @Column(length = 200)
    public String brandOrStore;

    public Integer dispatchBusinessDays;

    @ElementCollection
    @CollectionTable(
            name = "order_items",
            schema = "orders",
            joinColumns = @JoinColumn(name = "order_id"))
    @OrderColumn(name = "item_position")
    public List<OrderItem> items = new ArrayList<>();

    @Embedded public ShippingAddress address;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public State state;

    @Enumerated(EnumType.STRING)
    public Delivery delivery;

    public UUID reservationId;
    public Instant expiresAt;
    public Long unitPrice;
    public Long shippingFee;
    public Long totalAmount;

    @Column(unique = true)
    public UUID paymentAttemptId;

    @Column(length = 64)
    public String paymentKeyHash;

    // 결제 식별값은 Payment 전용 비공개 승인 명령에만 포함하고 조회 이벤트·로그·HTTP 응답에는 노출하지 않습니다.
    @Column(length = 1000)
    public String paymentKey;

    public Instant paymentApprovedAt;

    @Column(length = 100)
    public String termsVersion;

    public Instant consentedAt;

    @Column(nullable = false, updatable = false)
    public Instant createdAt;

    public Instant updatedAt;
    public Instant confirmedAt;

    @Column(length = 100)
    public String carrier;

    @Column(length = 100)
    public String trackingNumber;

    public Instant shippedAt;
    public Instant deliveredAt;
    // 분산 작업 임대는 HTTP 동안 DB 잠금을 점유하지 않습니다. 재시작 후 만료된 임대를 다시 획득합니다.
    public UUID leaseToken;
    public Instant leaseUntil;
    public Instant nextAttemptAt;
    public int attempts;

    @Column(length = 100)
    public String lastFailureCode;

    protected PurchaseOrder() {}

    // 기존 생성자를 거쳐 UUID·초기 상태·시간과 컬렉션 초기화를 보존하며 JPA 기본 생성자도 유지합니다.
    @Builder
    public PurchaseOrder(UUID buyer, UUID key, String hash, UUID product, Instant now) {
        id = UUID.randomUUID();
        buyerId = buyer;
        requestKey = key;
        requestHash = hash;
        productId = product;
        state = State.RESERVING;
        createdAt = now;
        updatedAt = now;
        nextAttemptAt = now;
    }

    public boolean terminal() {
        return state == State.CONFIRMED || state == State.FAILED || state == State.EXPIRED;
    }

    @Override
    public String toString() {
        return "PurchaseOrder[id=" + id + ",state=" + state + "]";
    }
}
