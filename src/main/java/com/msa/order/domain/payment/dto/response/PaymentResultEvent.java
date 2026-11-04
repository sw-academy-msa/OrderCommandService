package com.msa.order.domain.payment.dto.response;

import com.msa.order.domain.order.client.dto.response.PaymentReply;

import jakarta.validation.constraints.*;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/** Payment의 영속 결과 Outbox로부터 받는 Kafka 계약이며 HTTP 응답이 아닙니다. */
@Builder
public record PaymentResultEvent(
        @NotNull UUID eventId,
        @Min(1) @Max(1) int schemaVersion,
        @NotBlank @Pattern(regexp = "PAYMENT_RESULT") String type,
        @NotNull UUID orderId,
        @NotNull UUID paymentAttemptId,
        @Positive long amount,
        @PositiveOrZero long version,
        @NotNull PaymentReply.Status status,
        Instant approvedAt,
        @NotNull Instant occurredAt) {
    /** 빌더에 담긴 개인정보·결제키·토큰·이벤트 본문을 로그에 노출하지 않습니다. */
    public static class PaymentResultEventBuilder {
        @Override
        public String toString() {
            return "PaymentResultEventBuilder[REDACTED]";
        }
    }

    @Override
    public String toString() {
        return "PaymentResultEvent[eventId=" + eventId + "]";
    }
}
