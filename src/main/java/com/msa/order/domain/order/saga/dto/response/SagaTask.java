package com.msa.order.domain.order.saga.dto.response;

import com.msa.order.domain.order.entity.PurchaseOrder;

import java.time.Instant;
import java.util.*;

/** HTTP 응답이 아닌 임대 작업 내부 스냅샷입니다. 개인정보·결제키를 로그에 출력하지 않습니다. */
public record SagaTask(
        UUID orderId,
        UUID leaseToken,
        PurchaseOrder.State state,
        UUID productId,
        UUID buyerId,
        Map<UUID, Integer> items,
        UUID reservationId,
        Instant expiresAt,
        UUID paymentAttemptId,
        Long totalAmount,
        String paymentKey,
        Instant approvedAt) {
    @Override
    public String toString() {
        return "SagaTask[orderId=" + orderId + ",state=" + state + "]";
    }
}
