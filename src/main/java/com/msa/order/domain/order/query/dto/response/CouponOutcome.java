package com.msa.order.domain.order.query.dto.response;

import java.util.UUID;

/** 쿠폰 복구에 필요한 상태만 반환하여 주소와 결제 식별값을 숨깁니다. */
public record CouponOutcome(
        UUID orderId, String phase, boolean couponApplied, long couponDiscount) {
    @Override
    public String toString() {
        return "CouponOutcome[REDACTED]";
    }
}
