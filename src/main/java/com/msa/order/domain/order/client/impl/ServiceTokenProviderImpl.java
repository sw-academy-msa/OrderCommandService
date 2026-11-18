package com.msa.order.domain.order.client.impl;

import com.msa.order.domain.order.client.*;
import com.msa.order.global.config.client.ClientsProperties;
import com.msa.order.global.config.security.ServiceTokenProperties;

import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** 동시 요청은 단일 발급으로 합칩니다. 발급 실패 때 유효 토큰만 재사용하고 만료 토큰으로 호출하지 않습니다. */
@Component
public class ServiceTokenProviderImpl implements ServiceTokenProvider {
    private final HttpTransport http;
    private final ClientsProperties clients;
    private final ServiceTokenProperties properties;
    private final JwtDecoder decoder;
    private final Clock clock;
    private String cached;
    private Instant expiresAt;
    private Instant refreshAt;
    private Instant retryAfter = Instant.MIN;

    public ServiceTokenProviderImpl(
            HttpTransport http,
            ClientsProperties clients,
            ServiceTokenProperties properties,
            JwtDecoder decoder,
            Clock clock) {
        this.http = http;
        this.clients = clients;
        this.properties = properties;
        this.decoder = decoder;
        this.clock = clock;
    }

    public synchronized String token(String scope) {
        if (!"product.stock".equals(scope)) {
            throw new RemoteFailure("SERVICE_SCOPE_NOT_ALLOWED", false);
        }
        // 기존 명시적 고정 토큰 설정/HTTP fixture와의 호환 경로입니다. 자동 모드에서는 fallback하지 않습니다.
        if (!properties.enabled()) {
            return clients.product().token();
        }
        Instant now = clock.instant();
        if (valid(now) && now.isBefore(refreshAt)) {
            return cached;
        }
        if (now.isBefore(retryAfter)) {
            return stillValidOrFail(now);
        }
        try {
            var form = new LinkedMultiValueMap<String, String>();
            form.add("grant_type", "client_credentials");
            form.add("scope", scope);
            var reply =
                    http.exchangeServiceCredentials(
                            clients.account(),
                            form,
                            properties.clientId(),
                            properties.clientSecret(),
                            properties.allowInsecureLocal());
            if (!"Bearer".equals(reply.tokenType())
                    || reply.accessToken() == null
                    || reply.expiresAt() == null
                    || reply.expiresIn() <= 0
                    || reply.expiresIn() > 900
                    || !scope.equals(reply.scope())) {
                throw new RemoteFailure("INVALID_SERVICE_TOKEN", false);
            }
            // 기존 Order JWT 검증기를 재사용해 RS256/JWKS/issuer/audience/exp/token_use/UUID를 확인합니다.
            var jwt = decoder.decode(reply.accessToken());
            var roles = jwt.getClaimAsStringList("roles");
            if (!properties.subject().toString().equals(jwt.getSubject())
                    || !properties.clientId().equals(jwt.getClaimAsString("client_id"))
                    || !"service".equals(jwt.getClaimAsString("principal_type"))
                    || !scope.equals(jwt.getClaimAsString("scope"))
                    || roles == null
                    || !roles.isEmpty()
                    || jwt.getExpiresAt() == null
                    || !jwt.getExpiresAt().equals(reply.expiresAt().truncatedTo(ChronoUnit.SECONDS))
                    || !jwt.getExpiresAt().isAfter(clock.instant())) {
                throw new RemoteFailure("INVALID_SERVICE_TOKEN", false);
            }
            cached = reply.accessToken();
            expiresAt = jwt.getExpiresAt();
            refreshAt = expiresAt.minus(properties.renewBefore());
            retryAfter = Instant.MIN;
            return cached;
        } catch (RuntimeException failure) {
            retryAfter = clock.instant().plusSeconds(5);
            return stillValidOrFail(clock.instant());
        }
    }

    private boolean valid(Instant now) {
        return cached != null && expiresAt != null && now.isBefore(expiresAt);
    }

    private String stillValidOrFail(Instant now) {
        if (valid(now)) {
            return cached;
        }
        throw new RemoteFailure("SERVICE_AUTH_UNAVAILABLE", false);
    }
}
