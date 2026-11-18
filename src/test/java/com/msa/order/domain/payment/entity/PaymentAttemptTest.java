package com.msa.order.domain.payment.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.msa.order.domain.order.client.dto.response.PaymentReply;

import org.junit.jupiter.api.Test;

import java.util.UUID;

/** 빌더에서도 최초 결과 이벤트 수용과 미승인 상태가 유지되는지 검증합니다. */
class PaymentAttemptTest {
    @Test
    void builderKeepsPendingStatusAndInitialResultVersion() {
        var id = UUID.randomUUID();
        var order = UUID.randomUUID();
        var attempt = PaymentAttempt.builder().attempt(id).order(order).amount(6000).build();

        assertThat(attempt.id).isEqualTo(id);
        assertThat(attempt.orderId).isEqualTo(order);
        assertThat(attempt.amount).isEqualTo(6000);
        assertThat(attempt.status).isEqualTo(PaymentReply.Status.PENDING);
        assertThat(attempt.resultVersion).isEqualTo(-1);
        assertThat(attempt.commandVersion).isZero();
        assertThat(attempt.approvalRequested).isFalse();
        assertThat(attempt.compensationRequested).isFalse();
        assertThat(attempt.approvedAt).isNull();
    }
}
