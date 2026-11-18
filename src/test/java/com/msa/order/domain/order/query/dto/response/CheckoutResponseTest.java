package com.msa.order.domain.order.query.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.msa.order.domain.order.entity.PurchaseOrder;
import com.msa.order.domain.order.entity.ShippingAddress;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

/** 응답 생성 후 영속 배송지가 바뀌어도 응답이 변하지 않고 기존 JSON 필드를 유지하는지 검증합니다. */
class CheckoutResponseTest {
    @Test
    void addressIsAnIndependentDtoAndPreservesJsonFields() {
        var order =
                new PurchaseOrder(
                        java.util.UUID.randomUUID(),
                        java.util.UUID.randomUUID(),
                        "fixture-hash",
                        java.util.UUID.randomUUID(),
                        Instant.EPOCH);
        order.address =
                new ShippingAddress("합성 수령인", "01000000000", "00000", "합성 도로", "합성 상세", "합성 메모");
        var response = CheckoutResponse.from(order, Instant.parse("2030-01-01T00:00:00Z"));
        order.address.roadAddress = "변경된 도로";

        assertThat(response.address()).isInstanceOf(CheckoutResponse.ShippingAddressResponse.class);
        assertThat(response.address().roadAddress()).isEqualTo("합성 도로");
        var address = new ObjectMapper().valueToTree(response.address());
        assertThat(address.get("recipientName").asText()).isEqualTo("합성 수령인");
        assertThat(address.get("recipientPhone").asText()).isEqualTo("01000000000");
        assertThat(address.get("postalCode").asText()).isEqualTo("00000");
        assertThat(address.get("roadAddress").asText()).isEqualTo("합성 도로");
        assertThat(address.get("detailAddress").asText()).isEqualTo("합성 상세");
        assertThat(address.get("deliveryMemo").asText()).isEqualTo("합성 메모");
        assertThat(response.address().toString()).isEqualTo("ShippingAddressResponse[REDACTED]");
    }

    @Test
    void addressRemainsNullBeforeSelection() {
        var order =
                new PurchaseOrder(
                        java.util.UUID.randomUUID(),
                        java.util.UUID.randomUUID(),
                        "fixture-hash",
                        java.util.UUID.randomUUID(),
                        Instant.EPOCH);
        assertThat(CheckoutResponse.from(order, Instant.EPOCH).address()).isNull();
    }
}
