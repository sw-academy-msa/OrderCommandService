package com.msa.order.domain.order.entity;

import jakarta.persistence.*;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/** 진행 상태·실패 코드·처리 주체 이력입니다. 개인정보나 업스트림 오류 원문은 저장하지 않습니다. */
@Entity
@Table(
        name = "order_history",
        schema = "orders",
        indexes = @Index(columnList = "orderId,occurredAt"))
public class OrderHistory {
    /** 빌더에 담긴 개인정보·결제키·토큰·이벤트 본문을 로그에 노출하지 않습니다. */
    public static class OrderHistoryBuilder {
        @Override
        public String toString() {
            return "OrderHistoryBuilder[REDACTED]";
        }
    }

    @Id public UUID id;

    @Column(nullable = false)
    public UUID orderId;

    @Column(nullable = false, length = 100)
    public String action;

    public UUID actorId;

    @Column(nullable = false)
    public Instant occurredAt;

    protected OrderHistory() {}

    // 기존 생성자를 거쳐 UUID·초기 상태·시간과 컬렉션 초기화를 보존하며 JPA 기본 생성자도 유지합니다.
    @Builder
    public OrderHistory(UUID orderId, String action, UUID actor, Instant now) {
        id = UUID.randomUUID();
        this.orderId = orderId;
        this.action = action;
        actorId = actor;
        occurredAt = now;
    }
}
