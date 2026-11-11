package com.msa.order.domain.order.client;

import com.msa.order.domain.order.client.dto.response.ServiceTokenReply;
import com.msa.order.global.config.client.ClientsProperties;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.util.Map;

/**
 * 서비스 ID 주소(scheme://service-id/path)로 호출하고 인스턴스 선택은 Spring Cloud LoadBalancer에 맡깁니다. 상태 확인으로 죽은
 * 인스턴스를 빼고, 연결 실패는 다른 인스턴스로 재시도합니다. 내부 호출은 외부 Gateway를 왕복하지 않습니다.
 */
@Component
public class HttpTransport {
    private final RestClient rest;
    private final String scheme;
    private final JsonMapper json;

    public HttpTransport(
            @Qualifier("loadBalancedRestClient") RestClient rest,
            ClientsProperties properties,
            JsonMapper json) {
        this.rest = rest;
        this.scheme = properties.scheme();
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
        if (endpoint.serviceId() == null || !endpoint.serviceId().matches("[a-z][a-z0-9-]{1,99}")) {
            throw new RemoteFailure("CLIENT_NOT_CONFIGURED", false);
        }
        // 인스턴스는 전송 직전에 정해지므로 자격증명 TLS 요구는 scheme으로 보장합니다. https면 평문 인스턴스와는 연결 자체가 실패합니다.
        if (credentials && !"https".equals(scheme) && !allowInsecureLocal) {
            throw new RemoteFailure("SERVICE_AUTH_TLS_REQUIRED", false);
        }
        try {
            var request =
                    rest.method(method)
                            .uri(URI.create(scheme + "://" + endpoint.serviceId() + path))
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
        } catch (IllegalStateException failure) {
            // LoadBalancer가 고를 인스턴스가 없으면 요청을 보내지 않은 상태입니다. 고정 주소로 우회하지 않습니다.
            throw new RemoteFailure("SERVICE_UNAVAILABLE", false);
        } catch (RuntimeException failure) {
            throw new RemoteFailure("HTTP_RESULT_UNKNOWN", false);
        }
    }
}
