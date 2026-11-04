package com.msa.order.domain.order.saga.dto.response;

import com.msa.order.domain.order.entity.PurchaseOrder;

import lombok.Builder;

import java.time.Instant;
import java.util.*;

/** HTTP 응답이 아닌 임대 작업 내부 스냅샷입니다. 개인정보·결제키를 로그에 출력하지 않습니다. */
@Builder
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
    /** 빌더에 담긴 개인정보·결제키·토큰·이벤트 본문을 로그에 노출하지 않습니다. */
    public static class SagaTaskBuilder {
        @Override
        public String toString() {
            return "SagaTaskBuilder[REDACTED]";
        }
    }

    @Override
    public String toString() {
        return "SagaTask[orderId=" + orderId + ",state=" + state + "]";
    }
}
