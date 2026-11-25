package com.msa.order.domain.order.query.service;

import com.msa.order.domain.order.query.dto.response.CouponOutcome;

import java.util.UUID;

/** 쿠폰 잠금 복구를 위한 원본 조회 경계를 제공합니다. */
public interface CouponOutcomeService {
    CouponOutcome find(UUID orderId);
}
