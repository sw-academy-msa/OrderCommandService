package com.msa.order.domain.order.entity;

import jakarta.persistence.*;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/** 분석 ID를 주문 업무 원본과 분리하고 결제 명령 전용으로만 읽습니다. */
@Entity
@Table(name = "order_analytics_context", schema = "orders")
public class OrderAnalyticsContext {
    @Id public UUID orderId;
    public UUID analyticsId;

    @Column(length = 100)
    public String clientId;

    @Column(length = 40)
    public String sessionId;

    public Instant createdAt;

    protected OrderAnalyticsContext() {}

    @Builder
    public OrderAnalyticsContext(
            UUID orderId, UUID analyticsId, String clientId, String sessionId, Instant createdAt) {
        this.orderId = orderId;
        this.analyticsId = analyticsId;
        this.clientId = clientId;
        this.sessionId = sessionId;
        this.createdAt = createdAt;
    }

    /** 빌더의 가명 정보를 로그에서 숨깁니다. */
    public static class OrderAnalyticsContextBuilder {
        @Override
        public String toString() {
            return "OrderAnalyticsContextBuilder[REDACTED]";
        }
    }

    @Override
    public String toString() {
        return "OrderAnalyticsContext[REDACTED]";
    }
}
