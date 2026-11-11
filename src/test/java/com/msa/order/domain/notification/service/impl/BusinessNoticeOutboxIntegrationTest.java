package com.msa.order.domain.notification.service.impl;

import static org.assertj.core.api.Assertions.*;

import com.msa.order.domain.notification.dto.response.BusinessNotice;
import com.msa.order.domain.notification.service.BusinessNoticeService;
import com.msa.order.domain.outbox.repository.OutboxEventRepository;
import com.msa.order.global.config.database.OrderDatabaseTestSupport;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.ObjectMapper;

import java.util.*;

/** 실제 DB에서 Outbox 멱등성, 로컬 롤백과 원문 민감정보 미포함을 검증합니다. */
@SpringBootTest
@ActiveProfiles("test")
class BusinessNoticeOutboxIntegrationTest extends OrderDatabaseTestSupport {
    @Autowired BusinessNoticeService notices;
    @Autowired OutboxEventRepository outbox;
    @Autowired PlatformTransactionManager manager;
    @Autowired ObjectMapper json;

    @Test
    void sameBusinessVersionHasOneStableEvent() {
        UUID account = UUID.randomUUID(), aggregate = UUID.randomUUID();
        var tx = new TransactionTemplate(manager);
        tx.executeWithoutResult(
                s -> {
                    notices.enqueue(
                            account,
                            "ORDER_CONFIRMED",
                            aggregate,
                            2,
                            "ORDER",
                            aggregate,
                            null,
                            null);
                    notices.enqueue(
                            account,
                            "ORDER_CONFIRMED",
                            aggregate,
                            2,
                            "ORDER",
                            aggregate,
                            null,
                            null);
                });
        UUID id = id(account, aggregate);
        var row = outbox.findById(id).orElseThrow();
        assertThat(row.topic).isEqualTo("stockdrop.notifications.order.v1");
        assertThat(row.partitionKey).isEqualTo(aggregate.toString());
        var event = json.readValue(row.payload, BusinessNotice.class);
        assertThat(event.accountId()).isEqualTo(account);
        assertThat(event.aggregateVersion()).isEqualTo(2);
        assertThat(event.source()).isEqualTo("ORDER");
        assertThat(json.readTree(row.payload).propertyNames())
                .doesNotContain("reason", "address", "paymentKey", "phone", "email");
        String original = row.payload;
        tx.executeWithoutResult(
                s ->
                        notices.enqueue(
                                account,
                                "ORDER_CONFIRMED",
                                aggregate,
                                2,
                                "ORDER",
                                aggregate,
                                null,
                                null));
        assertThat(outbox.findById(id).orElseThrow().payload).isEqualTo(original);
    }

    @Test
    void rollbackNeverLeavesNotificationBehind() {
        UUID account = UUID.randomUUID(), aggregate = UUID.randomUUID();
        assertThatThrownBy(
                        () ->
                                new TransactionTemplate(manager)
                                        .executeWithoutResult(
                                                s -> {
                                                    notices.enqueue(
                                                            account,
                                                            "ORDER_CONFIRMED",
                                                            aggregate,
                                                            2,
                                                            "ORDER",
                                                            aggregate,
                                                            null,
                                                            null);
                                                    throw new IllegalStateException(
                                                            "synthetic rollback");
                                                }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(outbox.findById(id(account, aggregate))).isEmpty();
    }

    @Test
    void cannotEmitOutsideBusinessTransaction() {
        assertThatThrownBy(
                        () ->
                                notices.enqueue(
                                        UUID.randomUUID(),
                                        "ORDER_CONFIRMED",
                                        UUID.randomUUID(),
                                        2,
                                        "ORDER",
                                        UUID.randomUUID(),
                                        null,
                                        null))
                .isInstanceOf(
                        org.springframework.transaction.IllegalTransactionStateException.class);
    }

    UUID id(UUID account, UUID aggregate) {
        return UUID.nameUUIDFromBytes(
                ("ORDER:ORDER_CONFIRMED:" + aggregate + ":2:" + account)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
