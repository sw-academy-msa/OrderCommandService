package com.msa.order.domain.order.saga.dto.response;

import com.msa.order.domain.order.client.dto.response.*;
import com.msa.order.domain.order.entity.PurchaseOrder;

/** 외부 응답을 검증한 뒤 로컬 상태 전이로 전달하는 내부 DTO입니다. */
public record SagaOutcome(
        PurchaseOrder.State state, StockReply stock, PaymentReply payment, String failure) {}
