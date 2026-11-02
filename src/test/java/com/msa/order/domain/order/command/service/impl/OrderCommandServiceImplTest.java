package com.msa.order.domain.order.command.service.impl;

import static com.msa.order.domain.order.entity.PurchaseOrder.State.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.msa.order.domain.inquiry.command.dto.request.InquiryRequests;
import com.msa.order.domain.inquiry.command.service.OrderInquiryService;
import com.msa.order.domain.order.client.HttpDependencyFixtures;
import com.msa.order.domain.order.client.dto.response.PaymentReply;
import com.msa.order.domain.order.command.dto.request.OrderRequests;
import com.msa.order.domain.order.command.service.*;
import com.msa.order.domain.order.entity.*;
import com.msa.order.domain.order.query.dto.response.CheckoutResponse;
import com.msa.order.domain.order.query.service.OrderStatusQueryService;
import com.msa.order.domain.order.repository.OrderRepository;
import com.msa.order.domain.order.saga.dto.response.SagaOutcome;
import com.msa.order.domain.order.saga.service.*;
import com.msa.order.global.config.database.OrderDatabaseTestSupport;
import com.msa.order.global.error.BusinessException;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.json.JsonMapper;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** 실제 PostgreSQL·HTTP로 주문 멱등/영속 Saga/소유권을 검증합니다. 토스 실제 승인 테스트가 아닙니다. */
@SpringBootTest(
        properties = {"springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(OrderDatabaseTestSupport.Time.class)
class OrderCommandServiceImplTest extends OrderDatabaseTestSupport {
    static final HttpDependencyFixtures HTTP = new HttpDependencyFixtures();

    @DynamicPropertySource
    static void clients(DynamicPropertyRegistry r) {
        for (String name : List.of("product", "payment", "account")) {
            r.add("order.clients." + name + ".enabled", () -> "true");
            r.add("order.clients." + name + ".base-url", HTTP::url);
        }
        r.add("order.clients.product.token", () -> "fixture-product-token");
        r.add("order.clients.payment.token", () -> "fixture-payment-token");
    }

    @Autowired OrderCommandService commands;
    @Autowired OrderStatusQueryService queries;
    @Autowired OrderRepository orders;
    @Autowired OrderJournal journal;
    @Autowired OrderSagaService saga;
    @Autowired SagaStore sagaStore;
    @Autowired OrderWriteStore writeStore;
    @Autowired OrderInquiryService inquiries;
    @Autowired MutableClock clock;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired com.msa.order.domain.payment.service.PaymentResultService paymentResults;
    UUID buyer;
    UUID product;
    UUID option;
    UUID address;

    @BeforeEach
    void setup() {
        clock.set(Instant.parse("2030-01-01T00:00:00Z"));
        HTTP.reset(clock);
        jdbc.update("delete from orders.payment_result_receipts");
        jdbc.update("delete from orders.payment_attempts");
        jdbc.update("delete from orders.outbox_events");
        jdbc.update("delete from orders.order_history");
        jdbc.update("delete from orders.inquiry_images");
        jdbc.update("delete from orders.order_inquiry_messages");
        jdbc.update("delete from orders.order_inquiries");
        tx.executeWithoutResult(s -> orders.deleteAll());
        buyer = UUID.randomUUID();
        product = UUID.randomUUID();
        option = UUID.randomUUID();
        address = UUID.randomUUID();
    }

    OrderRequests.Create request() {
        return new OrderRequests.Create(
                UUID.randomUUID(),
                product,
                List.of(
                        new OrderRequests.Item(option, 2),
                        new OrderRequests.Item(UUID.randomUUID(), 1)),
                address);
    }

    CheckoutResponse create() {
        return commands.create(buyer, request(), "fixture-buyer-token");
    }

    OrderRequests.Payment payment() {
        return new OrderRequests.Payment(
                UUID.randomUUID(), "fixture-payment-key", true, "checkout-v1");
    }

    CheckoutResponse paid() {
        var o = create();
        commands.payment(buyer, o.orderId(), payment());
        return result(o.orderId(), PaymentReply.Status.APPROVED, clock.instant());
    }

    @Test
    void unexpectedTermsVersionCannotStartPayment() {
        var o = create();
        assertThatThrownBy(
                        () ->
                                commands.payment(
                                        buyer,
                                        o.orderId(),
                                        new OrderRequests.Payment(
                                                UUID.randomUUID(),
                                                "fixture-key",
                                                true,
                                                "unrecognized-version")))
                .isInstanceOf(BusinessException.class);
        assertThat(HTTP.approveCalls.get()).isZero();
        assertThat(queries.buyer(buyer, o.orderId()).state()).isEqualTo(HELD);
    }

    @Test
    void multipleOptionsUseSourcePriceAndOneShippingFee() {
        var o = create();
        assertThat(o.state()).isEqualTo(HELD);
        assertThat(o.items()).hasSize(2);
        assertThat(o.totalAmount()).isEqualTo(6000L);
        assertThat(o.shippingFee()).isEqualTo(3000L);
        assertThat(o.dispatchBusinessDays()).isEqualTo(3);
        assertThat(o.sellerId()).isEqualTo(HTTP.seller);
        assertThat(o.expiresAt()).isEqualTo(clock.instant().plusSeconds(300));
        assertThat(HTTP.productAuthenticated.get()).isTrue();
        assertThat(HTTP.accountAuthenticated.get()).isTrue();
    }

    @Test
    void duplicateOrderDoesNotExtendReservationAndChangedRequestIsRejected() {
        var r = request();
        var first = commands.create(buyer, r, "fixture-buyer-token");
        clock.set(clock.instant().plusSeconds(60));
        var second = commands.create(buyer, r, "fixture-buyer-token");
        assertThat(second.orderId()).isEqualTo(first.orderId());
        assertThat(second.expiresAt()).isEqualTo(first.expiresAt());
        assertThat(HTTP.reserveCalls.get()).isEqualTo(1);
        var changed =
                new OrderRequests.Create(
                        r.requestKey(),
                        product,
                        List.of(new OrderRequests.Item(option, 1)),
                        address);
        assertThatThrownBy(() -> commands.create(buyer, changed, "fixture-buyer-token"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void concurrentDuplicateCreatesOnlyOneOrderAndReservation() throws Exception {
        var r = request();
        var pool = Executors.newFixedThreadPool(5);
        try {
            List<Callable<UUID>> calls = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                calls.add(() -> commands.create(buyer, r, "fixture-buyer-token").orderId());
            }
            var ids = new HashSet<UUID>();
            for (var result : pool.invokeAll(calls)) {
                ids.add(result.get());
            }
            assertThat(ids).hasSize(1);
            assertThat(orders.count()).isEqualTo(1);
            assertThat(HTTP.reservations).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void reserveResponseLossIsRecoveredWithSameOrderAndDeadline() {
        HTTP.reserveReplyLost = true;
        var first = create();
        if (first.state() == RESERVING) {
            clock.set(clock.instant().plusSeconds(65));
            saga.recover();
        }
        var restored = queries.buyer(buyer, first.orderId());
        assertThat(restored.state()).isEqualTo(HELD);
        assertThat(HTTP.reservations).hasSize(1);
        assertThat(restored.expiresAt()).isEqualTo(Instant.parse("2030-01-01T00:05:00Z"));
    }

    @Test
    void stockRejectionNeverCallsPayment() {
        HTTP.stockDenied = true;
        var o = create();
        assertThat(o.state()).isEqualTo(FAILED);
        assertThat(HTTP.approveCalls.get()).isZero();
        assertThat(o.failureCode()).isEqualTo("PRODUCT_STOCK");
    }

    @Test
    void accountFailureDoesNotCreateOrder() {
        HTTP.accountUnavailable = true;
        assertThatThrownBy(this::create)
                .isInstanceOf(com.msa.order.domain.order.client.RemoteFailure.class);
        assertThat(orders.count()).isZero();
        assertThat(HTTP.reserveCalls.get()).isZero();
    }

    @Test
    void duplicateOptionIsRejectedBeforeRemoteCalls() {
        var r =
                new OrderRequests.Create(
                        UUID.randomUUID(),
                        product,
                        List.of(
                                new OrderRequests.Item(option, 1),
                                new OrderRequests.Item(option, 2)),
                        address);
        assertThatThrownBy(() -> commands.create(buyer, r, "fixture-buyer-token"))
                .isInstanceOf(BusinessException.class);
        assertThat(orders.count()).isZero();
        assertThat(HTTP.reserveCalls.get()).isZero();
    }

    @Test
    void paymentWithinDeadlineConfirmsOrderAndRetryDoesNotApproveAgain() {
        var o = create();
        var r = payment();
        commands.payment(buyer, o.orderId(), r);
        var done = result(o.orderId(), PaymentReply.Status.APPROVED, clock.instant());
        assertThat(done.state()).isEqualTo(CONFIRMED);
        assertThat(done.delivery()).isEqualTo(PurchaseOrder.Delivery.PREPARING);
        commands.payment(buyer, o.orderId(), r);
        assertThat(commandCount("APPROVE")).isEqualTo(1);
        assertThat(HTTP.reservations.get(o.orderId()).status()).isEqualTo("CONFIRMED");
        assertThat(orders.findById(o.orderId()).orElseThrow().paymentKey).isNull();
        assertThatThrownBy(
                        () ->
                                commands.payment(
                                        buyer,
                                        o.orderId(),
                                        new OrderRequests.Payment(
                                                r.requestKey(),
                                                "changed-key",
                                                true,
                                                r.termsVersion())))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void approvalReplyLossQueriesResultWithoutApprovingAgain() {
        var o = create();
        // 결과 이벤트가 아직 도착하지 않은 상황을 재현합니다.
        var result = commands.payment(buyer, o.orderId(), payment());
        assertThat(result.state()).isEqualTo(VERIFYING_PAYMENT);
        clock.set(clock.instant().plusSeconds(65));
        saga.recover();
        result(o.orderId(), PaymentReply.Status.APPROVED, Instant.parse("2030-01-01T00:00:00Z"));
        assertThat(queries.buyer(buyer, o.orderId()).state()).isEqualTo(CONFIRMED);
        assertThat(commandCount("APPROVE")).isEqualTo(1);
        assertThat(commandCount("CHECK")).isGreaterThan(0);
    }

    @Test
    void uncertainPaymentIsNotReleasedEvenAfterFiveMinutesAndCanConfirmLateResponse() {
        var o = create();
        HTTP.paymentStatus = PaymentReply.Status.PENDING;
        var result = commands.payment(buyer, o.orderId(), payment());
        assertThat(result.state()).isEqualTo(VERIFYING_PAYMENT);
        clock.set(clock.instant().plusSeconds(301));
        saga.recover();
        assertThat(queries.buyer(buyer, o.orderId()).state()).isEqualTo(VERIFYING_PAYMENT);
        assertThat(HTTP.releaseCalls.get()).isZero();
        assertThat(HTTP.reservations.get(o.orderId()).status()).isEqualTo("PAYMENT_PROCESSING");
        result(o.orderId(), PaymentReply.Status.APPROVED, Instant.parse("2030-01-01T00:00:00Z"));
        assertThat(queries.buyer(buyer, o.orderId()).state()).isEqualTo(CONFIRMED);
    }

    @Test
    void actualLateApprovalIsCompensatedBeforeRelease() {
        var o = create();
        commands.payment(buyer, o.orderId(), payment());
        var pending = result(o.orderId(), PaymentReply.Status.APPROVED, o.expiresAt());
        assertThat(pending.state()).isEqualTo(COMPENSATING_PAYMENT);
        var done = result(o.orderId(), PaymentReply.Status.CANCELED, o.expiresAt());
        assertThat(done.state()).isEqualTo(FAILED);
        assertThat(commandCount("CANCEL")).isEqualTo(1);
        assertThat(HTTP.releaseCalls.get()).isEqualTo(1);
        assertThat(HTTP.reservations.get(o.orderId()).status()).isEqualTo("RELEASED");
    }

    @Test
    void declinedPaymentReleasesStock() {
        var o = create();
        HTTP.paymentStatus = PaymentReply.Status.DECLINED;
        commands.payment(buyer, o.orderId(), payment());
        assertThat(result(o.orderId(), PaymentReply.Status.DECLINED, null).state())
                .isEqualTo(FAILED);
        assertThat(HTTP.releaseCalls.get()).isEqualTo(1);
    }

    @Test
    void noPaymentExpiresAndCannotStartPaymentAtBoundary() {
        var o = create();
        clock.set(o.expiresAt());
        assertThatThrownBy(() -> commands.payment(buyer, o.orderId(), payment()))
                .isInstanceOf(BusinessException.class);
        saga.recover();
        assertThat(queries.buyer(buyer, o.orderId()).state()).isEqualTo(EXPIRED);
        assertThat(HTTP.approveCalls.get()).isZero();
    }

    @Test
    void crashedLeaseCanBeReclaimedAndStaleCompletionCannotOverwrite() {
        var r = request();
        UUID id =
                writeStore.create(
                        buyer,
                        r,
                        "synthetic-hash",
                        new ShippingAddress("테스트", "00000000000", "00000", "합성 주소", "", ""));
        var old = sagaStore.claim(id);
        assertThat(old).isNotNull();
        assertThat(sagaStore.claim(id)).isNull();
        clock.set(clock.instant().plusSeconds(31));
        saga.advance(id);
        assertThat(queries.buyer(buyer, id).state()).isEqualTo(HELD);
        sagaStore.complete(old, new SagaOutcome(FAILED, null, null, "STALE"));
        assertThat(queries.buyer(buyer, id).state()).isEqualTo(HELD);
    }

    @Test
    void buyerAndSellerOwnershipIsEnforced() {
        var o = create();
        assertThatThrownBy(() -> queries.buyer(UUID.randomUUID(), o.orderId()))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> commands.payment(UUID.randomUUID(), o.orderId(), payment()))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(
                        () ->
                                commands.shipment(
                                        UUID.randomUUID(),
                                        o.orderId(),
                                        new OrderRequests.Shipment("테스트 택배", "12345", o.version())))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void addressSnapshotCanChangeOnlyBeforePayment() {
        var o = create();
        commands.address(
                buyer,
                o.orderId(),
                new OrderRequests.Address(UUID.randomUUID(), o.version()),
                "fixture-buyer-token");
        var refreshed = queries.buyer(buyer, o.orderId());
        commands.payment(buyer, o.orderId(), payment());
        assertThatThrownBy(
                        () ->
                                commands.address(
                                        buyer,
                                        o.orderId(),
                                        new OrderRequests.Address(
                                                UUID.randomUUID(), refreshed.version()),
                                        "fixture-buyer-token"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void sellerShipmentThenManualDeliveryDoesNotMeanSettlement() {
        var o = paid();
        var request = new OrderRequests.Shipment("테스트 택배", "TEST-12345", o.version());
        var shipped = commands.shipment(HTTP.seller, o.orderId(), request);
        commands.shipment(HTTP.seller, o.orderId(), request);
        assertThat(shipped.delivery()).isEqualTo(PurchaseOrder.Delivery.SHIPPING);
        var done =
                commands.deliver(
                        HTTP.seller, o.orderId(), new OrderRequests.Delivery(shipped.version()));
        assertThat(done.delivery()).isEqualTo(PurchaseOrder.Delivery.DELIVERED);
        assertThat(done.state()).isEqualTo(CONFIRMED);
        assertThat(HTTP.compensationCalls.get()).isZero();
    }

    @Test
    void shipmentBeforePaymentAndSkippingDeliveryStatesAreRejected() {
        var o = create();
        assertThatThrownBy(
                        () ->
                                commands.shipment(
                                        HTTP.seller,
                                        o.orderId(),
                                        new OrderRequests.Shipment("택배", "123", o.version())))
                .isInstanceOf(BusinessException.class);
        var p = commands.payment(buyer, o.orderId(), payment());
        assertThatThrownBy(
                        () ->
                                commands.deliver(
                                        HTTP.seller,
                                        p.orderId(),
                                        new OrderRequests.Delivery(p.version())))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void inquiryIsPrivateAndSellerAnswersDoNotBecomeProductFaq() {
        var o = paid();
        var r = new InquiryRequests.Create(UUID.randomUUID(), "배송", "합성 문의", List.of());
        var inquiry = inquiries.create(buyer, o.orderId(), r);
        assertThat(inquiries.create(buyer, o.orderId(), r).id()).isEqualTo(inquiry.id());
        var answer = new InquiryRequests.Message(UUID.randomUUID(), "합성 답변");
        inquiries.message(HTTP.seller, o.orderId(), inquiry.id(), true, answer);
        var repeated = inquiries.message(HTTP.seller, o.orderId(), inquiry.id(), true, answer);
        assertThat(repeated.messages()).hasSize(2);
        assertThatThrownBy(() -> inquiries.get(UUID.randomUUID(), o.orderId(), inquiry.id(), false))
                .isInstanceOf(BusinessException.class);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from orders.outbox_events where topic ="
                                        + " 'stockdrop.orders.inquiries.private.v1'",
                                Long.class))
                .isEqualTo(2);
    }

    @Test
    void orderAndOutboxRollbackTogetherAndSensitiveKeyIsExcluded() {
        var o = paid();
        String all =
                String.join(
                        "",
                        jdbc.queryForList(
                                "select payload from orders.outbox_events where topic <>"
                                        + " 'stockdrop.payment.commands.private.v1'",
                                String.class));
        assertThat(all)
                .doesNotContain("paymentKey", "fixture-payment-key", "leaseToken", "Authorization");
        long before = jdbc.queryForObject("select count(*) from orders.outbox_events", Long.class);
        assertThatThrownBy(
                        () ->
                                tx.executeWithoutResult(
                                        status -> {
                                            var entity = orders.lock(o.orderId()).orElseThrow();
                                            entity.carrier = "롤백 대상";
                                            journal.changed(entity, "ROLLBACK_TEST", buyer);
                                            throw new IllegalStateException("rollback");
                                        }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("select count(*) from orders.outbox_events", Long.class))
                .isEqualTo(before);
        assertThat(orders.findById(o.orderId()).orElseThrow().carrier).isNull();
    }

    @Test
    void mvcRejectsAnonymousWrongRoleInvalidInputAndForgedUserHeader() throws Exception {
        mvc.perform(
                        post("/api/v1/orders")
                                .header("X-User-Id", buyer)
                                .contentType("application/json")
                                .content(json.writeValueAsString(request())))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        post("/api/v1/orders")
                                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SELLER")))
                                .contentType("application/json")
                                .content(json.writeValueAsString(request())))
                .andExpect(status().isForbidden());
        var invalid =
                new OrderRequests.Create(
                        UUID.randomUUID(),
                        product,
                        List.of(new OrderRequests.Item(option, 0)),
                        address);
        mvc.perform(
                        post("/api/v1/orders")
                                .with(
                                        jwt().jwt(j -> j.subject(buyer.toString()))
                                                .authorities(
                                                        new SimpleGrantedAuthority("ROLE_BUYER")))
                                .contentType("application/json")
                                .content(json.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        get("/api/v1/orders")
                                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_BUYER"))))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void mvcCheckoutAndOpenApiContainOnlySupportedOperations() throws Exception {
        var o = create();
        mvc.perform(
                        get("/api/v1/orders/" + o.orderId() + "/checkout")
                                .with(
                                        jwt().jwt(j -> j.subject(buyer.toString()))
                                                .authorities(
                                                        new SimpleGrantedAuthority("ROLE_BUYER"))))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("totalAmount").value(6000));
        String spec =
                mvc.perform(get("/v3/api-docs"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(spec)
                .contains("/api/v1/orders", "/shipment", "/inquiries")
                .doesNotContain("/returns", "/cancellations", "arrivalDate", "firstComeRank");
        var path = java.nio.file.Path.of("build/generated/order-command.openapi.json");
        java.nio.file.Files.createDirectories(path.getParent());
        java.nio.file.Files.writeString(path, spec);
    }

    CheckoutResponse result(UUID id, PaymentReply.Status status, Instant approvedAt) {
        var o = orders.findById(id).orElseThrow();
        long version =
                jdbc.queryForObject(
                        "select result_version + 1 from orders.payment_attempts where id = ?",
                        Long.class,
                        o.paymentAttemptId);
        var event =
                new com.msa.order.domain.payment.dto.response.PaymentResultEvent(
                        UUID.randomUUID(),
                        1,
                        "PAYMENT_RESULT",
                        id,
                        o.paymentAttemptId,
                        o.totalAmount,
                        version,
                        status,
                        approvedAt,
                        clock.instant());
        paymentResults.apply(o.paymentAttemptId.toString(), json.writeValueAsBytes(event));
        saga.advance(id);
        return queries.buyer(buyer, id);
    }

    long commandCount(String action) {
        return jdbc.queryForObject(
                "select count(*) from orders.outbox_events where topic ="
                    + " 'stockdrop.payment.commands.private.v1' and payload::jsonb->>'action' = ?",
                Long.class,
                action);
    }
}
