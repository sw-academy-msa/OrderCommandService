package com.msa.order.domain.order.client.impl;

import com.msa.order.domain.order.client.*;
import com.msa.order.domain.order.client.dto.response.StockReply;
import com.msa.order.global.config.client.ClientsProperties;

import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

/** 기존 Product 재고 API 경로를 사용하며 인증 토큰이 없으면 네트워크 호출 전 차단합니다. */
@Component
public class ProductClientImpl implements ProductClient {
    private final HttpTransport http;
    private final ClientsProperties properties;
    private final ServiceTokenProvider tokens;

    public ProductClientImpl(
            HttpTransport http, ClientsProperties properties, ServiceTokenProvider tokens) {
        this.http = http;
        this.properties = properties;
        this.tokens = tokens;
    }

    public StockReply reserve(UUID order, UUID product, UUID buyer, Map<UUID, Integer> items) {
        return call(
                HttpMethod.POST,
                "",
                Map.of(
                        "orderId",
                        order,
                        "productId",
                        product,
                        "buyerId",
                        buyer,
                        "items",
                        items.entrySet().stream()
                                .map(i -> Map.of("optionId", i.getKey(), "quantity", i.getValue()))
                                .toList()));
    }

    public StockReply get(UUID id) {
        return call(HttpMethod.GET, "/" + id, null);
    }

    public StockReply begin(UUID id, UUID attempt) {
        return call(
                HttpMethod.POST,
                "/" + id + "/payment-processing",
                Map.of("paymentAttemptId", attempt));
    }

    public StockReply confirm(UUID id, UUID attempt, Instant at) {
        return call(
                HttpMethod.POST,
                "/" + id + "/confirmation",
                Map.of("paymentAttemptId", attempt, "approvedAt", at));
    }

    public StockReply release(UUID id, UUID attempt, boolean failed) {
        var body = new HashMap<String, Object>();
        body.put("paymentAttemptId", attempt);
        body.put("paymentFailureConfirmed", failed);
        return call(HttpMethod.POST, "/" + id + "/release", body);
    }

    private StockReply call(HttpMethod method, String path, Object body) {
        return http.exchange(
                properties.product(),
                method,
                "/internal/v1/stock-reservations" + path,
                body,
                tokens.token("product.stock"),
                StockReply.class);
    }
}
