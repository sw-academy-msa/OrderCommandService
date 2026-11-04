package com.msa.order.domain.order.saga.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.msa.order.domain.order.entity.PurchaseOrder;

import org.junit.jupiter.api.Test;

import java.util.UUID;

/** 새 빌더의 자동 문자열 표현이 결제키를 노출하지 않는지 검증합니다. */
class SagaTaskTest {
    @Test
    void builderAndTaskDoNotExposePaymentKeyInLogs() {
        var builder =
                SagaTask.builder()
                        .orderId(UUID.randomUUID())
                        .state(PurchaseOrder.State.APPROVING_PAYMENT)
                        .paymentKey("fixture-payment-key");

        assertThat(builder.toString()).isEqualTo("SagaTaskBuilder[REDACTED]");
        assertThat(builder.build().toString()).doesNotContain("fixture-payment-key");
    }
}
