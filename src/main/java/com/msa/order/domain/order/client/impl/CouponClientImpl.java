package com.msa.order.domain.order.client.impl;

import com.msa.order.domain.order.client.*;
import com.msa.order.domain.order.client.dto.response.*;
import com.msa.order.domain.order.saga.dto.response.SagaTask;
import com.msa.order.global.config.client.ClientsProperties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 쿠폰 장애가 확보한 재고를 버리지 않도록 세 번 재시도 후 할인 없이 진행합니다. */
@Component
public class CouponClientImpl implements CouponClient {
    private final HttpTransport http;
    private final ClientsProperties clients;
    private final ServiceTokenProvider tokens;
    private final boolean enabled;

    public CouponClientImpl(
            HttpTransport http,
            ClientsProperties clients,
            ServiceTokenProvider tokens,
            @Value("${order.coupon.enabled:false}") boolean enabled) {
        this.http = http;
        this.clients = clients;
        this.tokens = tokens;
        this.enabled = enabled;
    }

    public CouponReply lock(SagaTask t, StockReply r) {
        if (t.couponIssueId() == null || !"REQUESTED".equals(t.couponState())) return null;
        if (!enabled) return CouponReply.skipped("SKIPPED", "COUPON_DISABLED");
        if (t.buyerId().equals(r.sellerId()))
            return CouponReply.skipped("REJECTED", "COUPON_SELF_PRODUCT");
        long items;
        try {
            items =
                    Math.multiplyExact(
                            r.unitPrice(),
                            t.items().values().stream().mapToLong(Integer::longValue).sum());
        } catch (ArithmeticException overflow) {
            return CouponReply.skipped("SKIPPED", "AMOUNT_OVERFLOW");
        }
        try {
            var reply =
                    http.exchange(
                            clients.coupon(),
                            HttpMethod.POST,
                            "/internal/v1/coupon-locks",
                            Map.of(
                                    "orderId",
                                    t.orderId(),
                                    "couponIssueId",
                                    t.couponIssueId(),
                                    "accountId",
                                    t.buyerId(),
                                    "productId",
                                    t.productId(),
                                    "sellerId",
                                    r.sellerId(),
                                    "itemsAmount",
                                    items,
                                    "holdUntil",
                                    r.expiresAt()),
                            tokens.token("coupon.lock"),
                            Envelope.class);
            var c = reply.data();
            if (!reply.success()
                    || c == null
                    || !c.valid()
                    || !t.orderId().equals(c.orderId())
                    || !t.couponIssueId().equals(c.couponIssueId())
                    || items < c.minItemsAmount())
                throw new RemoteFailure("COUPON_UNAVAILABLE", false);
            return c;
        } catch (RemoteFailure failure) {
            if (failure.definitive()) return CouponReply.skipped("REJECTED", failure.code());
            if (t.couponLockAttempts() >= 3)
                return CouponReply.skipped("SKIPPED", "COUPON_UNAVAILABLE");
            throw new RemoteFailure("COUPON_UNAVAILABLE", false);
        }
    }

    /** 공통 응답의 성공과 데이터 계약을 함께 검증합니다. */
    public record Envelope(boolean success, String code, String message, CouponReply data) {
        @Override
        public String toString() {
            return "Envelope[REDACTED]";
        }
    }
}
