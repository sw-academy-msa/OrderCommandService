package com.msa.order.domain.order.client.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.msa.order.domain.order.client.*;
import com.msa.order.domain.order.client.dto.response.*;
import com.msa.order.domain.order.saga.dto.response.SagaTask;
import com.msa.order.global.config.client.ClientsProperties;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

/** 잠금 실패의 상한과 본인 상품 차단 및 금액 경계를 검증합니다. */
class CouponClientImplTest {
    @Test
    void amountAndRateLeaveAtLeastOneHundredAndAvoidOverflow() {
        var c =
                new CouponReply(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "LOCKED",
                        null,
                        "AMOUNT",
                        3000L,
                        null,
                        null,
                        0L,
                        1L);
        assertThat(c.discount(30000)).isEqualTo(3000);
        assertThat(c.discount(2000)).isEqualTo(1900);
        assertThat(c.discount(50)).isZero();
        var rate =
                new CouponReply(
                        c.orderId(),
                        c.couponIssueId(),
                        "LOCKED",
                        null,
                        "RATE",
                        null,
                        1000,
                        3000L,
                        0L,
                        1L);
        assertThat(rate.discount(10009)).isEqualTo(1000);
        assertThat(rate.discount(Long.MAX_VALUE)).isEqualTo(3000);
    }

    @Test
    void transientFailuresRetryThreeTimesThenSkipAndSelfProductNeverCallsHttp() {
        var http = mock(HttpTransport.class);
        var token = mock(ServiceTokenProvider.class);
        var endpoint = new ClientsProperties.Endpoint(true, "coupon-service", "fixture");
        var properties = new ClientsProperties("http", null, null, null, endpoint);
        var client = new CouponClientImpl(http, properties, token, true);
        when(token.token("coupon.lock")).thenThrow(new RemoteFailure("UNAVAILABLE", false));
        var buyer = UUID.randomUUID();
        var product = UUID.randomUUID();
        var option = UUID.randomUUID();
        var task =
                SagaTask.builder()
                        .orderId(UUID.randomUUID())
                        .buyerId(buyer)
                        .productId(product)
                        .items(Map.of(option, 1))
                        .couponIssueId(UUID.randomUUID())
                        .couponState("REQUESTED")
                        .couponLockAttempts(2)
                        .build();
        var stock =
                StockReply.builder()
                        .sellerId(UUID.randomUUID())
                        .unitPrice(30000L)
                        .expiresAt(Instant.now().plusSeconds(300))
                        .build();
        assertThatThrownBy(() -> client.lock(task, stock)).isInstanceOf(RemoteFailure.class);
        var exhausted =
                SagaTask.builder()
                        .orderId(task.orderId())
                        .buyerId(buyer)
                        .productId(product)
                        .items(task.items())
                        .couponIssueId(task.couponIssueId())
                        .couponState("REQUESTED")
                        .couponLockAttempts(3)
                        .build();
        assertThat(client.lock(exhausted, stock).state()).isEqualTo("SKIPPED");
        var own = StockReply.builder().sellerId(buyer).build();
        assertThat(client.lock(task, own).rejectCode()).isEqualTo("COUPON_SELF_PRODUCT");
        verifyNoInteractions(http);
    }
}
