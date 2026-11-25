package com.msa.order.domain.order.saga.scheduling;

import com.msa.order.domain.order.command.service.AnalyticsRetentionService;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 분석 수집을 켠 환경에서만 일 단위 정리를 실행합니다. */
@Component
@ConditionalOnProperty(name = "ORDER_ANALYTICS_ENABLED", havingValue = "true")
public class AnalyticsRetentionJob {
    private final AnalyticsRetentionService service;

    public AnalyticsRetentionJob(AnalyticsRetentionService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "PT24H")
    public void purge() {
        service.purge();
    }
}
