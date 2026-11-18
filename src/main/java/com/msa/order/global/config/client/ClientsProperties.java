package com.msa.order.global.config.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 내부 호출 대상은 Eureka 서비스 ID로만 지정합니다. 고정 주소 우회 경로는 두지 않습니다. scheme은 기본 https이며 로컬 프로필만 http를
 * 씁니다. 토큰은 출력하지 않습니다.
 */
@ConfigurationProperties("order.clients")
public record ClientsProperties(
        String scheme, Endpoint product, Endpoint payment, Endpoint account) {
    public ClientsProperties {
        scheme = scheme == null || scheme.isBlank() ? "https" : scheme;
        if (!java.util.Set.of("http", "https").contains(scheme)) {
            throw new IllegalArgumentException("내부 호출 scheme은 http 또는 https만 허용합니다.");
        }
    }

    public record Endpoint(boolean enabled, String serviceId, String token) {
        @Override
        public String toString() {
            return "Endpoint[REDACTED]";
        }
    }
}
