package com.msa.order.domain.order.client;

import com.msa.order.domain.order.client.dto.response.CouponReply;
import com.msa.order.domain.order.client.dto.response.StockReply;
import com.msa.order.domain.order.saga.dto.response.SagaTask;

/** 재고 확보 뒤 같은 주문 키로 쿠폰 잠금을 요청합니다. */
public interface CouponClient {
    CouponReply lock(SagaTask task, StockReply stock);
}
