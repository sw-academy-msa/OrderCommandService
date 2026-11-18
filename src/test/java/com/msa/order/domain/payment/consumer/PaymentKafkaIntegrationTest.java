package com.msa.order.domain.payment.consumer;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import com.msa.order.domain.order.client.PaymentClient;
import com.msa.order.domain.order.client.dto.response.PaymentReply;
import com.msa.order.domain.order.entity.PurchaseOrder;
import com.msa.order.domain.order.repository.OrderRepository;
import com.msa.order.domain.outbox.scheduling.OutboxPublishJob;
import com.msa.order.domain.outbox.service.OutboxPublisher;
import com.msa.order.domain.payment.InvalidPaymentEventException;
import com.msa.order.domain.payment.dto.response.PaymentResultEvent;
import com.msa.order.domain.payment.service.PaymentResultService;
import com.msa.order.global.config.database.OrderDatabaseTestSupport;

import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.serialization.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.kafka.KafkaContainer;

import tools.jackson.databind.json.JsonMapper;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** 실제 KRaft/PostgreSQL로 발행·결과 소비·중복·역전·DLT·트랜잭션을 검증합니다. 토스 승인이 아닙니다. */
@SpringBootTest(
        properties = {
            "order.payment-events.enabled=true",
            "spring.kafka.listener.auto-startup=true",
            "order.outbox.publisher-enabled=true",
            "order.outbox.poll-ms=3600000"
        })
@ActiveProfiles("test")
@Import(OrderDatabaseTestSupport.Time.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PaymentKafkaIntegrationTest extends OrderDatabaseTestSupport {
    static final String COMMAND = "stockdrop.payment.commands.private.v1";
    static final String RESULT = "stockdrop.payment.results.private.v1";
    static final KafkaContainer KAFKA =
            new KafkaContainer("apache/kafka:4.1.0")
                    .withEnv("KAFKA_HEAP_OPTS", "-Xms256m -Xmx256m");

    static {
        KAFKA.start();
        try (var admin = Admin.create(Map.of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
            admin.createTopics(
                            List.of(
                                    new NewTopic(COMMAND, 2, (short) 1),
                                    new NewTopic(RESULT, 2, (short) 1),
                                    new NewTopic(RESULT + ".DLT", 2, (short) 1),
                                    new NewTopic("stockdrop.orders.private.v1", 2, (short) 1)))
                    .all()
                    .get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void kafka(DynamicPropertyRegistry r) {
        r.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    // 기동 직후 예약 작업이 테스트가 넣은 Outbox 행을 먼저 잠그지 않게 합니다. 발행은 테스트가 직접 호출합니다.
    @MockitoBean OutboxPublishJob publishJob;

    @Autowired PaymentClient client;
    @Autowired PaymentResultService results;
    @Autowired OrderRepository orders;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;
    @Autowired JsonMapper json;
    @Autowired KafkaTemplate<String, byte[]> kafka;
    @Autowired OutboxPublisher publisher;
    @Autowired KafkaListenerEndpointRegistry registry;
    @Autowired org.springframework.kafka.listener.DefaultErrorHandler errors;
    UUID order, attempt;

    @BeforeEach
    void setup() {
        clock.set(Instant.parse("2030-01-01T00:00:00Z"));
        attempt = UUID.randomUUID();
        order =
                tx.execute(
                        s -> {
                            var o =
                                    new PurchaseOrder(
                                            UUID.randomUUID(),
                                            UUID.randomUUID(),
                                            "synthetic",
                                            UUID.randomUUID(),
                                            clock.instant());
                            o.state = PurchaseOrder.State.APPROVING_PAYMENT;
                            o.paymentAttemptId = attempt;
                            o.paymentKey = "synthetic-payment-key";
                            o.totalAmount = 6000L;
                            o.expiresAt = clock.instant().plusSeconds(300);
                            return orders.saveAndFlush(o).id;
                        });
    }

    @AfterAll
    void stopConsumer() {
        registry.getListenerContainer("order-payment-results").stop();
    }

    PaymentResultEvent event(long version, PaymentReply.Status status, Instant approvedAt) {
        return new PaymentResultEvent(
                UUID.randomUUID(),
                1,
                "PAYMENT_RESULT",
                order,
                attempt,
                6000L,
                version,
                status,
                approvedAt,
                clock.instant());
    }

    void receive(PaymentResultEvent e) throws Exception {
        kafka.send(RESULT, attempt.toString(), json.writeValueAsBytes(e)).get(20, TimeUnit.SECONDS);
    }

    String status() {
        return jdbc.queryForObject(
                "select status from orders.payment_attempts where id = ?", String.class, attempt);
    }

    @Test
    void requestIsAtomicIdempotentAndActuallyPublished() throws Exception {
        assertThat(client.approve(order, attempt, 6000L, "synthetic-payment-key").status())
                .isEqualTo(PaymentReply.Status.PENDING);
        client.approve(order, attempt, 6000L, "synthetic-payment-key");
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from orders.outbox_events where partition_key = ?"
                                        + " and topic = ?",
                                Long.class,
                                attempt.toString(),
                                COMMAND))
                .isEqualTo(1);
        for (int i = 0; i < 100 && publisher.publishNext(); i++) {}
        try (var consumer = consumer()) {
            consumer.subscribe(List.of(COMMAND));
            var found = new ArrayList<ConsumerRecord<String, byte[]>>();
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (found.isEmpty() && System.nanoTime() < end) {
                for (var r : consumer.poll(Duration.ofMillis(500))) {
                    if (r.key().equals(attempt.toString())) {
                        found.add(r);
                    }
                }
            }
            assertThat(found).hasSize(1);
            assertThat(json.readTree(found.getFirst().value()).path("action").asText())
                    .isEqualTo("APPROVE");
        }
    }

    @Test
    void requestAndOutboxRollbackTogether() {
        assertThatThrownBy(
                        () ->
                                tx.executeWithoutResult(
                                        s -> {
                                            client.approve(
                                                    order, attempt, 6000L, "synthetic-payment-key");
                                            throw new IllegalStateException("rollback");
                                        }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from orders.payment_attempts where id=?",
                                Long.class,
                                attempt))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from orders.outbox_events where partition_key=?",
                                Long.class,
                                attempt.toString()))
                .isZero();
    }

    @Test
    void duplicateOldAndConflictingResultsCannotCorruptState() throws Exception {
        client.approve(order, attempt, 6000L, "synthetic-payment-key");
        var approved = event(2, PaymentReply.Status.APPROVED, clock.instant());
        receive(approved);
        receive(approved);
        receive(event(1, PaymentReply.Status.PENDING, null));
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(status()).isEqualTo("APPROVED"));
        assertThatThrownBy(
                        () ->
                                results.apply(
                                        attempt.toString(),
                                        json.writeValueAsBytes(
                                                event(2, PaymentReply.Status.DECLINED, null))))
                .isInstanceOf(InvalidPaymentEventException.class);
        assertThatThrownBy(
                        () ->
                                results.apply(
                                        attempt.toString(),
                                        json.writeValueAsBytes(
                                                event(3, PaymentReply.Status.DECLINED, null))))
                .isInstanceOf(InvalidPaymentEventException.class);
        assertThat(status()).isEqualTo("APPROVED");
    }

    @Test
    void cancellationRequiresCommandAndThenFencesApproval() {
        client.approve(order, attempt, 6000L, "synthetic-payment-key");
        results.apply(
                attempt.toString(),
                json.writeValueAsBytes(event(1, PaymentReply.Status.APPROVED, clock.instant())));
        client.compensate(order, attempt);
        client.compensate(order, attempt);
        results.apply(
                attempt.toString(),
                json.writeValueAsBytes(event(2, PaymentReply.Status.CANCELED, clock.instant())));
        assertThat(status()).isEqualTo("CANCELED");
        assertThatThrownBy(
                        () ->
                                results.apply(
                                        attempt.toString(),
                                        json.writeValueAsBytes(
                                                event(
                                                        3,
                                                        PaymentReply.Status.APPROVED,
                                                        clock.instant()))))
                .isInstanceOf(InvalidPaymentEventException.class);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from orders.outbox_events where partition_key=?"
                                        + " and payload::jsonb->>'action'='CANCEL'",
                                Long.class,
                                attempt.toString()))
                .isEqualTo(1);
    }

    @Test
    void malformedResultGoesToDltAndFollowingResultStillApplies() throws Exception {
        client.approve(order, attempt, 6000L, "synthetic-payment-key");
        kafka.send(
                        RESULT,
                        attempt.toString(),
                        "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .get(20, TimeUnit.SECONDS);
        receive(event(1, PaymentReply.Status.APPROVED, clock.instant()));
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(status()).isEqualTo("APPROVED"));
        try (var consumer = consumer()) {
            consumer.subscribe(List.of(RESULT + ".DLT"));
            boolean found = false;
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (!found && System.nanoTime() < end) {
                for (var r : consumer.poll(Duration.ofMillis(500))) {
                    found |= attempt.toString().equals(r.key());
                }
            }
            assertThat(found).isTrue();
        }
    }

    @Test
    void databaseFailureDoesNotLoseResultAndRecovers() throws Exception {
        client.approve(order, attempt, 6000L, "synthetic-payment-key");
        var failures = new java.util.concurrent.atomic.AtomicInteger();
        errors.setRetryListeners((record, failure, delivery) -> failures.incrementAndGet());
        jdbc.execute(
                "alter table orders.payment_result_receipts rename to"
                    + " payment_result_receipts_unavailable");
        try {
            receive(event(1, PaymentReply.Status.APPROVED, clock.instant()));
            await().atMost(Duration.ofSeconds(20))
                    .untilAsserted(() -> assertThat(failures.get()).isGreaterThan(0));
            assertThat(status()).isEqualTo("PENDING");
        } finally {
            jdbc.execute(
                    "alter table orders.payment_result_receipts_unavailable rename to"
                        + " payment_result_receipts");
        }
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(status()).isEqualTo("APPROVED"));
    }

    @Test
    void resultAndReceiptRollbackTogether() {
        client.approve(order, attempt, 6000L, "synthetic-payment-key");
        var e = event(1, PaymentReply.Status.APPROVED, clock.instant());
        assertThatThrownBy(
                        () ->
                                tx.executeWithoutResult(
                                        s -> {
                                            results.apply(
                                                    attempt.toString(), json.writeValueAsBytes(e));
                                            throw new IllegalStateException("rollback");
                                        }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(status()).isEqualTo("PENDING");
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from orders.payment_result_receipts where id=?",
                                Long.class,
                                e.eventId()))
                .isZero();
    }

    KafkaConsumer<String, byte[]> consumer() {
        return new KafkaConsumer<>(
                Map.of(
                        "bootstrap.servers",
                        KAFKA.getBootstrapServers(),
                        "group.id",
                        "payment-test-" + UUID.randomUUID(),
                        "auto.offset.reset",
                        "earliest"),
                new StringDeserializer(),
                new ByteArrayDeserializer());
    }
}
