package com.msa.order.domain.order.client;

import com.msa.order.domain.order.client.dto.response.*;
import com.sun.net.httpserver.*;

import tools.jackson.databind.json.JsonMapper;

import java.io.*;
import java.net.InetSocketAddress;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** 실제 HTTP transport를 통과하는 합성 Account/Product/Payment입니다. 존재하지 않는 Payment 구현을 보증하지 않습니다. */
public class HttpDependencyFixtures implements AutoCloseable {
    public final HttpServer server;
    public final UUID seller = UUID.randomUUID();
    public Clock clock = Clock.systemUTC();
    public volatile boolean stockDenied;
    public volatile boolean reserveReplyLost;
    public volatile boolean paymentReplyLost;
    public volatile boolean accountUnavailable;
    public volatile PaymentReply.Status paymentStatus = PaymentReply.Status.APPROVED;
    public volatile Instant approvalOverride;
    public final ConcurrentMap<UUID, StockReply> reservations = new ConcurrentHashMap<>();
    public final ConcurrentMap<UUID, PaymentReply> payments = new ConcurrentHashMap<>();
    public final AtomicInteger reserveCalls = new AtomicInteger();
    public final AtomicInteger approveCalls = new AtomicInteger();
    public final AtomicInteger queryCalls = new AtomicInteger();
    public final AtomicInteger releaseCalls = new AtomicInteger();
    public final AtomicInteger compensationCalls = new AtomicInteger();
    public final AtomicBoolean productAuthenticated = new AtomicBoolean();
    public final AtomicBoolean accountAuthenticated = new AtomicBoolean();
    private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public HttpDependencyFixtures() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(executor);
            server.createContext("/", this::handle);
            server.start();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void reset(Clock clock) {
        this.clock = clock;
        reservations.clear();
        payments.clear();
        stockDenied = false;
        reserveReplyLost = false;
        paymentReplyLost = false;
        accountUnavailable = false;
        paymentStatus = PaymentReply.Status.APPROVED;
        approvalOverride = null;
        for (var counter :
                List.of(reserveCalls, approveCalls, queryCalls, releaseCalls, compensationCalls)) {
            counter.set(0);
        }
        productAuthenticated.set(false);
        accountAuthenticated.set(false);
    }

    private void handle(HttpExchange e) throws IOException {
        try {
            String path = e.getRequestURI().getPath();
            String authorization = e.getRequestHeaders().getFirst("Authorization");
            if (path.startsWith("/api/v1/accounts/me/addresses/")) {
                accountAuthenticated.set("Bearer fixture-buyer-token".equals(authorization));
                if (accountUnavailable) {
                    respond(e, 503, Map.of("code", "ACCOUNT_DOWN"));
                    return;
                }
                UUID id = UUID.fromString(path.substring(path.lastIndexOf('/') + 1));
                respond(
                        e,
                        200,
                        new AddressReply(
                                id,
                                "합성 배송지",
                                "테스트 수령인",
                                "00000000000",
                                "00000",
                                "합성 주소",
                                "합성 상세",
                                "테스트 메모",
                                false,
                                clock.instant(),
                                clock.instant()));
                return;
            }
            if (path.startsWith("/internal/v1/stock-reservations")) {
                productAuthenticated.set("Bearer fixture-product-token".equals(authorization));
                if (!productAuthenticated.get()) {
                    respond(e, 403, Map.of("code", "COMMON_FORBIDDEN"));
                    return;
                }
                product(e, path);
                return;
            }
            if (path.startsWith("/internal/v1/payment-attempts")) {
                payment(e, path);
                return;
            }
            respond(e, 501, Map.of("code", "FIXTURE_NOT_IMPLEMENTED"));
        } catch (Exception ignored) {
            // fixture 오류에도 본문/토큰/배송지를 출력하지 않습니다.
            e.close();
        }
    }

    private synchronized void product(HttpExchange e, String path) throws IOException {
        String base = "/internal/v1/stock-reservations";
        var body = e.getRequestMethod().equals("GET") ? null : json.readTree(e.getRequestBody());
        if (path.equals(base)) {
            reserveCalls.incrementAndGet();
            if (stockDenied) {
                respond(e, 409, Map.of("code", "PRODUCT_STOCK"));
                return;
            }
            UUID order = UUID.fromString(body.path("orderId").asText());
            var r = reservations.get(order);
            if (r == null) {
                var items = new TreeMap<UUID, Integer>();
                var labels = new TreeMap<UUID, String>();
                for (var i : body.path("items")) {
                    UUID id = UUID.fromString(i.path("optionId").asText());
                    items.put(id, i.path("quantity").asInt());
                    labels.put(id, "합성 옵션");
                }
                r =
                        new StockReply(
                                UUID.randomUUID(),
                                order,
                                UUID.fromString(body.path("productId").asText()),
                                UUID.fromString(body.path("buyerId").asText()),
                                "HELD",
                                clock.instant().plusSeconds(300),
                                clock.instant(),
                                1000L,
                                3000L,
                                items,
                                seller,
                                "합성 상품",
                                "합성 스토어",
                                3,
                                labels);
                reservations.put(order, r);
            }
            if (reserveReplyLost) {
                reserveReplyLost = false;
                e.close();
                return;
            }
            respond(e, 200, r);
            return;
        }
        String[] parts = path.substring(base.length() + 1).split("/");
        UUID id = UUID.fromString(parts[0]);
        var r =
                reservations.values().stream()
                        .filter(v -> v.id().equals(id))
                        .findFirst()
                        .orElseThrow();
        String status = r.status();
        if (parts.length == 1
                && status.equals("HELD")
                && !clock.instant().isBefore(r.expiresAt())) {
            status = "EXPIRED";
        }
        if (parts.length > 1) {
            switch (parts[1]) {
                case "payment-processing" -> {
                    if (!clock.instant().isBefore(r.expiresAt()) && status.equals("HELD")) {
                        respond(e, 409, Map.of("code", "PRODUCT_EXPIRED"));
                        return;
                    }
                    status = "PAYMENT_PROCESSING";
                }
                case "confirmation" -> {
                    if (!Instant.parse(body.path("approvedAt").asText()).isBefore(r.expiresAt())) {
                        respond(e, 409, Map.of("code", "PRODUCT_EXPIRED"));
                        return;
                    }
                    status = "CONFIRMED";
                }
                case "release" -> {
                    if (status.equals("CONFIRMED")
                            || status.equals("PAYMENT_PROCESSING")
                                    && !body.path("paymentFailureConfirmed").asBoolean()) {
                        respond(e, 409, Map.of("code", "PRODUCT_STATE"));
                        return;
                    }
                    if (!Set.of("RELEASED", "EXPIRED").contains(status)) {
                        releaseCalls.incrementAndGet();
                    }
                    status = "RELEASED";
                }
                default -> {
                    respond(e, 501, Map.of("code", "FIXTURE_NOT_IMPLEMENTED"));
                    return;
                }
            }
        }
        var result =
                new StockReply(
                        r.id(),
                        r.orderId(),
                        r.productId(),
                        r.buyerId(),
                        status,
                        r.expiresAt(),
                        clock.instant(),
                        r.unitPrice(),
                        r.shippingFee(),
                        r.items(),
                        r.sellerId(),
                        r.productName(),
                        r.brandOrStore(),
                        r.dispatchBusinessDays(),
                        r.optionLabels());
        reservations.put(result.orderId(), result);
        respond(e, 200, result);
    }

    private synchronized void payment(HttpExchange e, String path) throws IOException {
        String base = "/internal/v1/payment-attempts";
        if (path.equals(base)) {
            approveCalls.incrementAndGet();
            var body = json.readTree(e.getRequestBody());
            UUID attempt = UUID.fromString(body.path("paymentAttemptId").asText());
            var r =
                    payments.computeIfAbsent(
                            attempt,
                            key ->
                                    new PaymentReply(
                                            UUID.fromString(body.path("orderId").asText()),
                                            attempt,
                                            body.path("amount").asLong(),
                                            paymentStatus,
                                            approvalOverride == null
                                                    ? clock.instant()
                                                    : approvalOverride));
            if (paymentReplyLost) {
                paymentReplyLost = false;
                e.close();
                return;
            }
            respond(e, 200, r);
            return;
        }
        String[] parts = path.substring(base.length() + 1).split("/");
        UUID id = UUID.fromString(parts[0]);
        var r = payments.get(id);
        if (r == null) {
            respond(e, 200, new PaymentReply(null, id, 0L, PaymentReply.Status.UNKNOWN, null));
            return;
        }
        if (parts.length > 1) {
            compensationCalls.incrementAndGet();
            paymentStatus = PaymentReply.Status.CANCELED;
        } else {
            queryCalls.incrementAndGet();
        }
        respond(
                e,
                200,
                new PaymentReply(
                        r.orderId(),
                        r.paymentAttemptId(),
                        r.amount(),
                        paymentStatus,
                        r.approvedAt()));
    }

    private void respond(HttpExchange e, int status, Object value) throws IOException {
        byte[] bytes = json.writeValueAsBytes(value);
        e.getResponseHeaders().set("Content-Type", "application/json");
        e.sendResponseHeaders(status, bytes.length);
        e.getResponseBody().write(bytes);
        e.close();
    }

    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
