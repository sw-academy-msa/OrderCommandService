package com.msa.order.domain.order.client.dto.response;

import java.math.BigInteger;
import java.util.UUID;

/** 잠금 조건 스냅샷을 검증하고 원 주문의 할인만 계산하는 내부 DTO입니다. */
public record CouponReply(
        UUID orderId,
        UUID couponIssueId,
        String state,
        String rejectCode,
        String discountType,
        Long amount,
        Integer rateBps,
        Long maxDiscount,
        Long minItemsAmount,
        Long definitionVersion) {
    public static CouponReply skipped(String state, String code) {
        return new CouponReply(null, null, state, code, null, null, null, null, null, null);
    }

    public boolean valid() {
        return "LOCKED".equals(state)
                && minItemsAmount != null
                && minItemsAmount >= 0
                && definitionVersion != null
                && definitionVersion >= 0
                && ("AMOUNT".equals(discountType) && amount != null && amount > 0
                        || "RATE".equals(discountType)
                                && rateBps != null
                                && rateBps > 0
                                && rateBps <= 10000
                                && maxDiscount != null
                                && maxDiscount > 0);
    }

    public long discount(long items) {
        if (!valid() || items < minItemsAmount) return 0;
        long raw =
                "AMOUNT".equals(discountType)
                        ? amount
                        : BigInteger.valueOf(items)
                                .multiply(BigInteger.valueOf(rateBps))
                                .divide(BigInteger.valueOf(10000))
                                .min(BigInteger.valueOf(maxDiscount))
                                .longValueExact();
        return Math.max(0, Math.min(raw, items - 100));
    }

    @Override
    public String toString() {
        return "CouponReply[REDACTED]";
    }
}
