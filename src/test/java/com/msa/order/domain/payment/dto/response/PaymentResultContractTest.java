package com.msa.order.domain.payment.dto.response;

import static org.assertj.core.api.Assertions.*;

import com.msa.order.domain.order.client.dto.response.PaymentReply;

import jakarta.validation.Validation;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Payment의 실제 대역 통합 테스트가 Kafka로 발행한 합성 결과를 Order DTO 계약으로 검증합니다. */
class PaymentResultContractTest {
    @Test
    void paymentProducedResultMatchesOrderContract() throws Exception {
        try (var input = getClass().getResourceAsStream("/contracts/payment-approved-result.json");
                var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(input).isNotNull();
            byte[] bytes = input.readAllBytes();
            var json = JsonMapper.builder().findAndAddModules().build();
            PaymentResultEvent event =
                    json.readerFor(PaymentResultEvent.class)
                            .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                            .readValue(bytes);
            assertThat(factory.getValidator().validate(event)).isEmpty();
            assertThat(event.status()).isEqualTo(PaymentReply.Status.APPROVED);
            assertThat(event.approvedAt()).isNotNull();
            assertThat(event.approvedAt().getNano() % 1000000).isZero();
            assertThat(new String(bytes, java.nio.charset.StandardCharsets.UTF_8))
                    .doesNotContain("paymentKey", "test_gsk", "clientKey");
        }
    }
}
