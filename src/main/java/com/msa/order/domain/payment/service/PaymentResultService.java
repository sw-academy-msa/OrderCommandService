package com.msa.order.domain.payment.service;

/** 결과 소비와 로컬 이력을 원자적으로 반영합니다. Kafka 스레드에서 Product HTTP를 호출하지 않습니다. */
public interface PaymentResultService {
    void apply(String key, byte[] payload);
}
