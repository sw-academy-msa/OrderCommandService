package com.msa.order.domain.order.saga.service;

import java.util.UUID;

/** 상태 전이 사이의 외부 호출을 수행하며 전체 흐름을 하나의 DB 트랜잭션으로 묶지 않습니다. */
public interface OrderSagaService {
    void advance(UUID orderId);

    void recover();
}
