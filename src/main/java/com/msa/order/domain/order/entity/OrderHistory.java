package com.msa.order.domain.order.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** 진행 상태·실패 코드·처리 주체 이력입니다. 개인정보나 업스트림 오류 원문은 저장하지 않습니다. */
@Entity
@Table(
        name = "order_history",
        schema = "orders",
        indexes = @Index(columnList = "orderId,occurredAt"))
public class OrderHistory {
    @Id public UUID id;

    @Column(nullable = false)
    public UUID orderId;

    @Column(nullable = false, length = 100)
    public String action;

    public UUID actorId;

    @Column(nullable = false)
    public Instant occurredAt;

    protected OrderHistory() {}

    public OrderHistory(UUID orderId, String action, UUID actor, Instant now) {
        id = UUID.randomUUID();
        this.orderId = orderId;
        this.action = action;
        actorId = actor;
        occurredAt = now;
    }
}
