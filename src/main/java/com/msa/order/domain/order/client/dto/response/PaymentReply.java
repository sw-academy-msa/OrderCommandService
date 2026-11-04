package com.msa.order.domain.order.client.dto.response;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/** Kafka 결과를 저장한 로컬 상태의 내부 반환 DTO입니다. HTTP 응답이 아니며 UNKNOWN은 실패가 아닙니다. */
@Builder
public record PaymentReply(
        UUID orderId, UUID paymentAttemptId, long amount, Status status, Instant approvedAt) {
    /** 빌더에 담긴 개인정보·결제키·토큰·이벤트 본문을 로그에 노출하지 않습니다. */
    public static class PaymentReplyBuilder {
        @Override
        public String toString() {
            return "PaymentReplyBuilder[REDACTED]";
        }
    }

    public enum Status {
        PENDING,
        APPROVED,
        DECLINED,
        CANCELED,
        UNKNOWN
    }
}
