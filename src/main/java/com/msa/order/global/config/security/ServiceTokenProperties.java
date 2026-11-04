package com.msa.order.global.config.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.UUID;

/** Order는 서비스 자격증명 원문만 보유하고 Account 서명키는 보유하지 않습니다. */
@ConfigurationProperties("order.service-auth")
public record ServiceTokenProperties(
        boolean enabled,
        String clientId,
        String clientSecret,
        UUID subject,
        Duration renewBefore,
        boolean allowInsecureLocal) {
    public ServiceTokenProperties {
        renewBefore = renewBefore == null ? Duration.ofSeconds(30) : renewBefore;
        if (enabled
                && (clientId == null
                        || !clientId.matches("[a-z][a-z0-9-]{2,99}")
                        || clientSecret == null
                        || clientSecret.isBlank()
                        || clientSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                                > 72
                        || subject == null
                        || renewBefore.isNegative()
                        || renewBefore.isZero()
                        || renewBefore.compareTo(Duration.ofMinutes(2)) > 0)) {
            throw new IllegalArgumentException("Order 서비스 인증 설정이 올바르지 않습니다.");
        }
    }

    @Override
    public String toString() {
        return "ServiceTokenProperties[REDACTED]";
    }
}
