package com.msa.order.domain.order.saga.dto.response;

import com.msa.order.domain.order.client.dto.response.*;
import com.msa.order.domain.order.entity.PurchaseOrder;

import lombok.Builder;

/** 외부 응답을 검증한 뒤 로컬 상태 전이로 전달하는 내부 DTO입니다. */
@Builder
public record SagaOutcome(
        PurchaseOrder.State state, StockReply stock, PaymentReply payment, String failure) {
    /** 빌더에 담긴 개인정보·결제키·토큰·이벤트 본문을 로그에 노출하지 않습니다. */
    public static class SagaOutcomeBuilder {
        @Override
        public String toString() {
            return "SagaOutcomeBuilder[REDACTED]";
        }
    }
}
