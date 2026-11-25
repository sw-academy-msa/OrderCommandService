package com.msa.order.domain.order.command.service;

/** 분석 문맥의 보존 기간을 적용합니다. */
public interface AnalyticsRetentionService {
    void purge();
}
