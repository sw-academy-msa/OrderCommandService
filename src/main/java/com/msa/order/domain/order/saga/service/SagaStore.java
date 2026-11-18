package com.msa.order.domain.order.saga.service;

import com.msa.order.domain.order.saga.dto.response.*;

import java.util.UUID;

/** 작업 임대와 결과 반영을 짧은 로컬 트랜잭션으로 분리합니다. */
public interface SagaStore {
    SagaTask claim(UUID orderId);

    UUID nextDue();

    void complete(SagaTask task, SagaOutcome outcome);

    void retry(SagaTask task, String failure);
}
