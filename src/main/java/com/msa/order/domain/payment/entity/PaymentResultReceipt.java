package com.msa.order.domain.payment.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** 이벤트 ID와 내용의 동일성을 보장하는 소비 이력입니다. 결과 반영과 같은 트랜잭션으로 INSERT합니다. */
@Entity
@Table(name = "payment_result_receipts", schema = "orders")
public class PaymentResultReceipt {
    @Id public UUID id;

    @Column(nullable = false, length = 64)
    public String payloadHash;

    @Column(nullable = false)
    public Instant processedAt;

    protected PaymentResultReceipt() {}

    public PaymentResultReceipt(UUID id, String hash, Instant now) {
        this.id = id;
        payloadHash = hash;
        processedAt = now;
    }
}
