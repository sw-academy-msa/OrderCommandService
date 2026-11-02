package com.msa.order.domain.payment.consumer;

import com.msa.order.domain.payment.service.PaymentResultService;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** 결과 저장 트랜잭션이 커밋된 뒤 record offset을 처리합니다. 원문/결제키를 출력하지 않습니다. */
@Component
@ConditionalOnProperty(name = "order.payment-events.enabled", havingValue = "true")
public class PaymentResultConsumer {
    private final PaymentResultService service;

    public PaymentResultConsumer(PaymentResultService service) {
        this.service = service;
    }

    @KafkaListener(
            id = "order-payment-results",
            idIsGroup = false,
            topics = "stockdrop.payment.results.private.v1")
    public void consume(ConsumerRecord<String, byte[]> record) {
        service.apply(record.key(), record.value());
    }
}
