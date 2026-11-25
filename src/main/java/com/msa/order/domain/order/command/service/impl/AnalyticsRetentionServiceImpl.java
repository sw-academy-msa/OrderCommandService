package com.msa.order.domain.order.command.service.impl;

import com.msa.order.domain.order.command.service.AnalyticsRetentionService;
import com.msa.order.domain.order.repository.OrderAnalyticsContextRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/** 주문 결제 분석 문맥을 30일 뒤 삭제하는 짧은 트랜잭션입니다. */
@Service
public class AnalyticsRetentionServiceImpl implements AnalyticsRetentionService {
    private final OrderAnalyticsContextRepository rows;
    private final Clock clock;

    public AnalyticsRetentionServiceImpl(OrderAnalyticsContextRepository rows, Clock clock) {
        this.rows = rows;
        this.clock = clock;
    }

    @Transactional(timeout = 5)
    public void purge() {
        rows.purge(clock.instant().minusSeconds(30L * 86400));
    }
}
