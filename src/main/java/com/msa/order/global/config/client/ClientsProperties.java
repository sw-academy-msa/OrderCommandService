package com.msa.order.global.config.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 운영은 서비스 탐색, 테스트는 명시적 HTTP fixture를 사용합니다. 토큰은 출력하지 않습니다. */
@ConfigurationProperties("order.clients")
public record ClientsProperties(
        boolean discoveryEnabled, Endpoint product, Endpoint payment, Endpoint account) {
    public record Endpoint(boolean enabled, String serviceId, String baseUrl, String token) {
        @Override
        public String toString() {
            return "Endpoint[REDACTED]";
        }
    }
}
