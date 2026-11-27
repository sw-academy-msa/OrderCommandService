package com.msa.order.domain.order.saga.dto.response;

import java.time.Instant;

/** HTTP 응답이 아닌 공유 DB 상태별 읽기 집계 projection입니다. */
public interface SagaMetricsSnapshot {
    String getState();
    long getOrders();
    Instant getOldest();
}
