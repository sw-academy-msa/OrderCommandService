package com.msa.order.domain.order.saga.scheduling;

import com.msa.order.domain.order.saga.service.OrderSagaService;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 영속 작업을 재개합니다. 복제본 간 중복 실행은 DB 임대 토큰으로 제어합니다. */
@Component
@ConditionalOnProperty(name = "order.saga.enabled", havingValue = "true", matchIfMissing = true)
public class OrderSagaRecoveryJob {
    private final OrderSagaService saga;

    public OrderSagaRecoveryJob(OrderSagaService saga) {
        this.saga = saga;
    }

    @Scheduled(fixedDelayString = "${order.saga.poll-ms:1000}", scheduler = "sagaScheduler")
    public void recover() {
        saga.recover();
    }
}
