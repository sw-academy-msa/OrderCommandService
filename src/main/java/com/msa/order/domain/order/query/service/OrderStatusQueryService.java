package com.msa.order.domain.order.query.service;

import com.msa.order.domain.order.query.dto.response.CheckoutResponse;

import java.util.UUID;

/** Command 작업 화면과 즉시 결과 확인용 원본 조회입니다. 별도 Order Query 책임과 구분합니다. */
public interface OrderStatusQueryService {
    CheckoutResponse buyer(UUID buyer, UUID order);

    CheckoutResponse seller(UUID seller, UUID order);
}
