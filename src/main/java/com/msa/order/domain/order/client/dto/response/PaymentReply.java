package com.msa.order.domain.order.client.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Kafka 결과를 저장한 로컬 상태의 내부 반환 DTO입니다. HTTP 응답이 아니며 UNKNOWN은 실패가 아닙니다. */
public record PaymentReply(
        UUID orderId, UUID paymentAttemptId, long amount, Status status, Instant approvedAt) {
    public enum Status {
        PENDING,
        APPROVED,
        DECLINED,
        CANCELED,
        UNKNOWN
    }
}
