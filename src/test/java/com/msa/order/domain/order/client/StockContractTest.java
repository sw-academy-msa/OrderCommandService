package com.msa.order.domain.order.client;

import static org.assertj.core.api.Assertions.*;

import com.msa.order.domain.order.client.dto.response.StockReply;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

/** 실제 Product 확보 서비스 테스트가 생성한 합성 응답과 Order 역직렬화 계약을 비교합니다. */
class StockContractTest {
    @Test
    void productProducedSnapshotCanBeConsumedWithoutQueryOrDirectSql() throws Exception {
        var json = JsonMapper.builder().findAndAddModules().build();
        try (var input = getClass().getResourceAsStream("/contracts/product-stock-reply.json")) {
            assertThat(input).isNotNull();
            var reply = json.readValue(input, StockReply.class);
            assertThat(reply.id()).isNotNull();
            assertThat(reply.orderId()).isNotNull();
            assertThat(reply.sellerId()).isNotNull();
            assertThat(reply.productName()).isEqualTo("테스트 상품");
            assertThat(reply.dispatchBusinessDays()).isEqualTo(3);
            assertThat(reply.optionLabels()).hasSize(2);
            assertThat(reply.items()).hasSize(2);
            assertThat(reply.optionLabels().keySet()).isEqualTo(reply.items().keySet());
        }
    }
}
