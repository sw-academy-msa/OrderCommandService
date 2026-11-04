package com.msa.order.domain.order.client;

import com.msa.order.domain.order.client.dto.response.ServiceTokenReply;
import com.msa.order.global.config.client.ClientsProperties;

import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.util.Map;

/** Eureka 인스턴스를 LoadBalancer로 선택합니다. 내부 호출은 외부 Gateway를 왕복하지 않습니다. */
@Component
public class HttpTransport {
    private final RestClient rest;
    private final LoadBalancerClient balancer;
    private final ClientsProperties properties;
    private final JsonMapper json;

    public HttpTransport(
            RestClient rest,
            LoadBalancerClient balancer,
            ClientsProperties properties,
            JsonMapper json) {
        this.rest = rest;
        this.balancer = balancer;
        this.properties = properties;
        this.json = json;
    }

    public <T> T exchange(
            ClientsProperties.Endpoint endpoint,
            HttpMethod method,
            String path,
            Object body,
            String token,
            Class<T> type) {
        if (!endpoint.enabled() || token == null || token.isBlank()) {
            throw new RemoteFailure("CLIENT_NOT_CONFIGURED", false);
        }
        return send(
                endpoint,
                method,
                path,
                body,
                type,
                h -> h.setBearerAuth(token),
                MediaType.APPLICATION_JSON,
                false,
                true);
    }

    /** 같은 URI 탐색·시간 제한·오류 처리 경로로 내부 토큰을 요청합니다. 자격증명을 URL/본문에 넣지 않습니다. */
    public ServiceTokenReply exchangeServiceCredentials(
            ClientsProperties.Endpoint endpoint,
            org.springframework.util.MultiValueMap<String, String> form,
            String clientId,
            String secret,
            boolean allowInsecureLocal) {
        return send(
                endpoint,
                HttpMethod.POST,
                "/internal/v1/auth/service-tokens",
                form,
                ServiceTokenReply.class,
                h -> h.setBasicAuth(clientId, secret),
                MediaType.APPLICATION_FORM_URLENCODED,
                true,
                allowInsecureLocal);
    }

    private <T> T send(
            ClientsProperties.Endpoint endpoint,
            HttpMethod method,
            String path,
            Object body,
            Class<T> type,
            java.util.function.Consumer<HttpHeaders> authenticate,
            MediaType contentType,
            boolean credentials,
            boolean allowInsecureLocal) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("HTTP를 DB 트랜잭션 안에서 호출할 수 없습니다.");
        }
        if (!endpoint.enabled()) {
            throw new RemoteFailure("CLIENT_NOT_CONFIGURED", false);
        }
        try {
            URI base;
            if (properties.discoveryEnabled()) {
                var instance = balancer.choose(endpoint.serviceId());
                if (instance == null) {
                    throw new RemoteFailure("SERVICE_UNAVAILABLE", false);
                }
                base = instance.getUri();
            } else {
                base = URI.create(endpoint.baseUrl());
            }
            if (!java.util.Set.of("http", "https").contains(base.getScheme())
                    || base.getHost() == null
                    || base.getUserInfo() != null) {
                throw new RemoteFailure("CLIENT_NOT_CONFIGURED", false);
            }
            if (credentials
                    && !"https".equals(base.getScheme())
                    && !(allowInsecureLocal
                            && java.util.Set.of("localhost", "127.0.0.1", "[::1]", "::1")
                                    .contains(base.getHost()))) {
                throw new RemoteFailure("SERVICE_AUTH_TLS_REQUIRED", false);
            }
            var request =
                    rest.method(method)
                            .uri(base.resolve(path))
                            .headers(
                                    h -> {
                                        authenticate.accept(h);
                                        String trace = org.slf4j.MDC.get("traceId");
                                        h.set(
                                                "X-Request-Id",
                                                trace == null
                                                        ? java.util.UUID.randomUUID().toString()
                                                        : trace);
                                    });
            if (body != null) {
                request.contentType(contentType).body(body);
            }
            T result =
                    request.retrieve()
                            .onStatus(
                                    status -> !status.is2xxSuccessful(),
                                    (req, response) -> {
                                        String code = "UPSTREAM_UNAVAILABLE";
                                        boolean definitive = false;
                                        if (response.getStatusCode().value() == 400
                                                || response.getStatusCode().value() == 404
                                                || response.getStatusCode().value() == 409) {
                                            try {
                                                Map<?, ?> error =
                                                        json.readValue(
                                                                response.getBody(), Map.class);
                                                Object candidate = error.get("code");
                                                if (candidate instanceof String value
                                                        && java.util.Set.of(
                                                                        "PRODUCT_STOCK",
                                                                        "PRODUCT_STATE",
                                                                        "PRODUCT_DUPLICATE",
                                                                        "PRODUCT_EXPIRED",
                                                                        "PRODUCT_NOT_FOUND",
                                                                        "PRODUCT_INVALID")
                                                                .contains(value)) {
                                                    code = value;
                                                    definitive = true;
                                                }
                                            } catch (Exception ignored) {
                                                // 알 수 없는 오류나 HTML은 실패 확정의 근거가 아닙니다.
                                            }
                                        }
                                        throw new RemoteFailure(code, definitive);
                                    })
                            .body(type);
            if (result == null) {
                throw new RemoteFailure("INVALID_REMOTE_RESPONSE", false);
            }
            return result;
        } catch (RemoteFailure failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new RemoteFailure("HTTP_RESULT_UNKNOWN", false);
        }
    }
}
