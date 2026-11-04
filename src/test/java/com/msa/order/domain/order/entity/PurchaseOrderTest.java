package com.msa.order.domain.order.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

/** 빌더가 주문 최초 상태·복구 일정·컬렉션 초기화 규칙을 보존하는지 검증합니다. */
class PurchaseOrderTest {
    @Test
    void builderKeepsInitialSagaStateAndIndependentItems() {
        var buyer = UUID.randomUUID();
        var now = Instant.parse("2030-01-01T00:00:00Z");
        var builder =
                PurchaseOrder.builder()
                        .buyer(buyer)
                        .key(UUID.randomUUID())
                        .hash("fixture-hash")
                        .product(UUID.randomUUID())
                        .now(now);
        var first = builder.build();
        var second = builder.build();
        first.items.add(OrderItem.builder().id(UUID.randomUUID()).quantity(2).build());

        assertThat(first.id).isNotNull().isNotEqualTo(second.id);
        assertThat(first.buyerId).isEqualTo(buyer);
        assertThat(first.state).isEqualTo(PurchaseOrder.State.RESERVING);
        assertThat(first.createdAt).isEqualTo(now);
        assertThat(first.updatedAt).isEqualTo(now);
        assertThat(first.nextAttemptAt).isEqualTo(now);
        assertThat(first.leaseToken).isNull();
        assertThat(first.leaseUntil).isNull();
        assertThat(second.items).isEmpty();
    }
}
