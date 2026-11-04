package com.msa.order.domain.order.command.dto.request;

import static org.assertj.core.api.Assertions.*;

import jakarta.validation.Validation;

import org.junit.jupiter.api.Test;

import java.util.UUID;

/** 토스의 paymentKey 최대 길이를 공개 주문 진입점에서도 적용해 영구 오류 명령 발행을 막습니다. */
class OrderRequestsTest {
    @Test
    void paymentKeyLengthMatchesProviderAndKafkaContract() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(
                            validator.validate(
                                    new OrderRequests.Payment(
                                            UUID.randomUUID(),
                                            "x".repeat(200),
                                            true,
                                            "checkout-v1")))
                    .isEmpty();
            assertThat(
                            validator.validate(
                                    new OrderRequests.Payment(
                                            UUID.randomUUID(),
                                            "x".repeat(201),
                                            true,
                                            "checkout-v1")))
                    .anyMatch(v -> v.getPropertyPath().toString().equals("paymentKey"));
        }
    }
}
