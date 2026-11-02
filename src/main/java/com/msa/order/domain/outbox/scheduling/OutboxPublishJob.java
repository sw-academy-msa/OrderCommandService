package com.msa.order.domain.outbox.scheduling;

import com.msa.order.domain.outbox.service.OutboxPublisher;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Kafka 전송은 주문 변경 이후 별도 내부 작업입니다. 발행 응답 원문을 로그에 남기지 않습니다. */
@Component
@ConditionalOnProperty(name = "order.outbox.publisher-enabled", havingValue = "true")
public class OutboxPublishJob {
    private final OutboxPublisher publisher;

    public OutboxPublishJob(OutboxPublisher publisher) {
        this.publisher = publisher;
    }

    @Scheduled(fixedDelayString = "${order.outbox.poll-ms:1000}", scheduler = "outboxScheduler")
    public void publish() {
        for (int i = 0; i < 20 && publisher.publishNext(); i++) {
            /* 실행당 처리량을 제한합니다. */
        }
    }
}
