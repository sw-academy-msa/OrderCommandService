package com.msa.order.domain.withdrawal.dto.response;

import lombok.Builder;

import java.util.*;

/** 내부 HTTP 응답이며 주문 개인정보나 결제키를 전달하지 않습니다. */
public final class WithdrawalResponses {
    private WithdrawalResponses() {}

    @Builder
    public record State(long activeOrders, long pendingSettlements) {}

    @Builder
    public record PaymentReference(UUID orderId, UUID paymentAttemptId, boolean approved) {}

    @Builder
    public record Payments(List<PaymentReference> references, boolean hasNext) {}
}
