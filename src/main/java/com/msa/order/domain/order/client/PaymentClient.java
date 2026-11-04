package com.msa.order.domain.order.client;

import com.msa.order.domain.order.client.dto.response.PaymentReply;

import java.util.UUID;

/** Kafka 명령을 Outbox에 저장하고 소비된 로컬 결제 결과를 읽습니다. 토스 호출은 Payment만 소유합니다. */
public interface PaymentClient {
    PaymentReply approve(UUID order, UUID attempt, long amount, String paymentKey);

    PaymentReply get(UUID attempt);

    PaymentReply compensate(UUID order, UUID attempt);
}
