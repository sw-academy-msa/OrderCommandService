package com.msa.order.domain.order.command.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.*;

/** 브라우저에는 원본 가격·판매자 ID·구매자 ID를 입력받지 않습니다. */
public final class OrderRequests {
    private OrderRequests() {}

    public record Item(@NotNull UUID optionId, @Positive int quantity) {}

    public record Create(
            @NotNull UUID requestKey,
            @NotNull UUID productId,
            @NotEmpty List<@NotNull @Valid Item> items,
            @NotNull UUID addressId) {}

    public record Address(@NotNull UUID addressId, @PositiveOrZero long expectedVersion) {}

    public record Payment(
            @NotNull UUID requestKey,
            @NotBlank @Size(max = 200) String paymentKey,
            @AssertTrue boolean agreed,
            @NotBlank @Size(max = 100) String termsVersion) {
        @Override
        public String toString() {
            return "Payment[REDACTED]";
        }
    }

    public record Shipment(
            @NotBlank @Size(max = 100) String carrier,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9-]{1,100}") String trackingNumber,
            @PositiveOrZero long expectedVersion) {}

    public record Delivery(@PositiveOrZero long expectedVersion) {}
}
