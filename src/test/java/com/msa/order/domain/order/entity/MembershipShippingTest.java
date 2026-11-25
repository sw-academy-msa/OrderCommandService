package com.msa.order.domain.order.entity;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

/** 플랫폼 배송비 지원은 원 배송비를 보존하고 큰 정수 금액에서도 범위를 넘지 않습니다. */
class MembershipShippingTest {
    @Test
    void preservesOriginalAndSupportsRateAmountAndLegacyOrders() {
        var order =
                PurchaseOrder.builder()
                        .buyer(UUID.randomUUID())
                        .key(UUID.randomUUID())
                        .product(UUID.randomUUID())
                        .hash("fixture")
                        .now(Instant.EPOCH)
                        .build();
        order.shippingFee = 3000L;
        assertThat(order.effectiveShippingFee()).isEqualTo(3000L);
        assertThat(order.membershipDiscount(3000L)).isZero();
        order.membershipEligible = true;
        order.membershipRateBps = 10000;
        assertThat(order.membershipDiscount(Long.MAX_VALUE)).isEqualTo(Long.MAX_VALUE);
        order.originalShippingFee = 3000L;
        order.membershipShippingDiscount = order.membershipDiscount(3000L);
        assertThat(order.effectiveShippingFee()).isZero();
        assertThat(order.shippingFee).isEqualTo(3000L);
        order.membershipRateBps = 3333;
        assertThat(order.membershipDiscount(3000L)).isEqualTo(999L);
        order.membershipRateBps = null;
        order.membershipAmount = 4000L;
        assertThat(order.membershipDiscount(3000L)).isEqualTo(3000L);
    }
}
