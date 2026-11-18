package com.msa.order.global.config.kafka;

import com.msa.order.domain.payment.InvalidPaymentEventException;

import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.*;
import org.springframework.util.backoff.FixedBackOff;

import java.time.Duration;

/** 영구 계약 오류는 DLT로 보내고 DB/발행 장애는 offset을 넘기지 않고 재시도합니다. */
@Configuration
@EnableKafka
@ConditionalOnProperty(name = "order.payment-events.enabled", havingValue = "true")
public class PaymentKafkaConfig {
    @Bean
    public DefaultErrorHandler paymentErrorHandler(KafkaTemplate<String, byte[]> template) {
        var recoverer =
                new DeadLetterPublishingRecoverer(
                        template,
                        (record, ex) ->
                                new TopicPartition(record.topic() + ".DLT", record.partition()));
        recoverer.setFailIfSendResultIsError(true);
        recoverer.setWaitForSendResultTimeout(Duration.ofSeconds(10));
        var handler =
                new DefaultErrorHandler(
                        recoverer, new FixedBackOff(1000L, FixedBackOff.UNLIMITED_ATTEMPTS));
        handler.addNotRetryableExceptions(InvalidPaymentEventException.class);
        return handler;
    }
}
