package com.msa.order.domain.payment.entity;

import com.msa.order.domain.order.client.dto.response.PaymentReply;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** Order 소유의 결제 통신 이력입니다. 실제 결제 원본·토스 호출은 Payment 서비스 책임입니다. */
@Entity
@Table(name = "payment_attempts", schema = "orders")
public class PaymentAttempt {
    @Id public UUID id;

    @Column(nullable = false, unique = true)
    public UUID orderId;

    @Column(nullable = false)
    public long amount;

    public long commandVersion;
    public long resultVersion = -1;

    @Column(length = 64)
    public String resultHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public PaymentReply.Status status;

    public Instant approvedAt;
    public boolean approvalRequested;
    public boolean compensationRequested;
    public Instant nextCheckAt;

    protected PaymentAttempt() {}

    public PaymentAttempt(UUID attempt, UUID order, long amount) {
        id = attempt;
        orderId = order;
        this.amount = amount;
        status = PaymentReply.Status.PENDING;
    }
}
