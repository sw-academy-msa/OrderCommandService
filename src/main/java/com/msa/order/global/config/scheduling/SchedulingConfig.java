package com.msa.order.global.config.scheduling;

import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** 업무 작업은 도메인에 두고 실행기만 구성합니다. 느린 Kafka/S3가 Saga 실행기를 막지 않습니다. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
    @Bean
    public ThreadPoolTaskScheduler sagaScheduler() {
        return scheduler("order-saga-", 2);
    }

    @Bean
    public ThreadPoolTaskScheduler outboxScheduler() {
        return scheduler("order-outbox-", 1);
    }

    @Bean
    public ThreadPoolTaskScheduler inquiryCleanupScheduler() {
        return scheduler("inquiry-cleanup-", 1);
    }

    private ThreadPoolTaskScheduler scheduler(String prefix, int size) {
        var s = new ThreadPoolTaskScheduler();
        s.setPoolSize(size);
        s.setThreadNamePrefix(prefix);
        s.setWaitForTasksToCompleteOnShutdown(true);
        s.setAwaitTerminationSeconds(15);
        return s;
    }
}
