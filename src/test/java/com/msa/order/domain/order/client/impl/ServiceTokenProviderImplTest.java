package com.msa.order.domain.order.client.impl;

import static org.assertj.core.api.Assertions.*;

import com.msa.order.domain.order.client.*;
import com.msa.order.global.config.client.*;
import com.msa.order.global.config.security.*;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.*;

import tools.jackson.databind.json.JsonMapper;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** 실제 HTTP Basic·RS256/JWKS·기존 JWT 검증기로 캐시/동시 갱신/만료 차단을 검증합니다. */
class ServiceTokenProviderImplTest {
    HttpServer server;
    RSAKey key;
    UUID subject;
    TestClock clock;
    AtomicInteger requests;
    boolean unavailable;
    boolean userToken;
    boolean wrongSignature;
    boolean wrongScope;
    ServiceTokenProviderImpl provider;
    HttpTransport transport;
    ClientsProperties clients;

    @BeforeEach
    void setup() throws Exception {
        key = new RSAKeyGenerator(2048).keyID("synthetic-service-key").generate();
        subject = UUID.randomUUID();
        clock = new TestClock();
        requests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var json = JsonMapper.builder().findAndAddModules().build();
        server.createContext(
                "/.well-known/jwks.json",
                e -> {
                    byte[] b =
                            new JWKSet(key.toPublicJWK())
                                    .toString()
                                    .getBytes(StandardCharsets.UTF_8);
                    e.getResponseHeaders().set("Content-Type", "application/json");
                    e.sendResponseHeaders(200, b.length);
                    e.getResponseBody().write(b);
                    e.close();
                });
        server.createContext(
                "/internal/v1/auth/service-tokens",
                e -> {
                    requests.incrementAndGet();
                    try {
                        String expected =
                                "Basic "
                                        + Base64.getEncoder()
                                                .encodeToString(
                                                        "order-command-service:synthetic-client-secret"
                                                                .getBytes(
                                                                        StandardCharsets
                                                                                .ISO_8859_1));
                        if (!expected.equals(e.getRequestHeaders().getFirst("Authorization"))
                                || unavailable) {
                            e.sendResponseHeaders(503, -1);
                            e.close();
                            return;
                        }
                        String form =
                                new String(
                                        e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                        if (!form.contains("grant_type=client_credentials")
                                || !form.contains("scope=product.stock")) {
                            e.sendResponseHeaders(400, -1);
                            e.close();
                            return;
                        }
                        String scope = wrongScope ? "other.scope" : "product.stock";
                        var claims =
                                new JWTClaimsSet.Builder()
                                        .issuer("https://account.test")
                                        .audience("stockdrop-api")
                                        .subject(subject.toString())
                                        .issueTime(Date.from(clock.instant()))
                                        .expirationTime(Date.from(clock.instant().plusSeconds(60)))
                                        .jwtID(UUID.randomUUID().toString())
                                        .claim("token_use", "access")
                                        .claim("roles", userToken ? List.of("BUYER") : List.of())
                                        .claim("principal_type", userToken ? "user" : "service")
                                        .claim("client_id", "order-command-service")
                                        .claim("scope", scope)
                                        .build();
                        var signed =
                                new SignedJWT(
                                        new JWSHeader.Builder(JWSAlgorithm.RS256)
                                                .keyID(key.getKeyID())
                                                .build(),
                                        claims);
                        signed.sign(
                                new RSASSASigner(
                                        wrongSignature
                                                ? new RSAKeyGenerator(2048).generate()
                                                : key));
                        byte[] b =
                                json.writeValueAsBytes(
                                        Map.of(
                                                "access_token",
                                                signed.serialize(),
                                                "token_type",
                                                "Bearer",
                                                "expires_in",
                                                60,
                                                "expires_at",
                                                clock.instant().plusSeconds(60),
                                                "scope",
                                                scope));
                        e.getResponseHeaders().set("Content-Type", "application/json");
                        e.sendResponseHeaders(200, b.length);
                        e.getResponseBody().write(b);
                        e.close();
                    } catch (Exception ignored) {
                        e.close();
                    }
                });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        var endpoint = new ClientsProperties.Endpoint(true, "account-service", base, "");
        clients =
                new ClientsProperties(
                        false,
                        new ClientsProperties.Endpoint(
                                true, "product-command-service", base, "legacy-fixture"),
                        endpoint,
                        endpoint);
        transport =
                new HttpTransport(new HttpClientConfig().internalRestClient(), null, clients, json);
        var decoder =
                new JwtConfig()
                        .jwtDecoder(
                                new AuthProperties(
                                        "https://account.test",
                                        "stockdrop-api",
                                        base + "/.well-known/jwks.json"),
                                clock);
        provider =
                new ServiceTokenProviderImpl(
                        transport,
                        clients,
                        new ServiceTokenProperties(
                                true,
                                "order-command-service",
                                "synthetic-client-secret",
                                subject,
                                Duration.ofSeconds(10),
                                true),
                        decoder,
                        clock);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void cachesAndRefreshesBeforeExpiry() {
        String first = provider.token("product.stock");
        assertThat(provider.token("product.stock")).isEqualTo(first);
        assertThat(requests.get()).isEqualTo(1);
        clock.now = clock.now.plusSeconds(51);
        String refreshed = provider.token("product.stock");
        assertThat(refreshed).isNotEqualTo(first);
        assertThat(requests.get()).isEqualTo(2);
    }

    @Test
    void concurrentRequestsAcquireOnlyOnce() throws Exception {
        var pool = Executors.newFixedThreadPool(10);
        try {
            List<Callable<String>> tasks = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                tasks.add(() -> provider.token("product.stock"));
            }
            var values = new HashSet<String>();
            for (var task : pool.invokeAll(tasks)) {
                values.add(task.get());
            }
            assertThat(values).hasSize(1);
            assertThat(requests.get()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void temporaryAccountFailureKeepsOnlyUnexpiredTokenAndThrottlesRetries() {
        String first = provider.token("product.stock");
        unavailable = true;
        clock.now = clock.now.plusSeconds(51);
        assertThat(provider.token("product.stock")).isEqualTo(first);
        int attempts = requests.get();
        assertThat(provider.token("product.stock")).isEqualTo(first);
        assertThat(requests.get()).isEqualTo(attempts);
        clock.now = clock.now.plusSeconds(10);
        assertThatThrownBy(() -> provider.token("product.stock"))
                .isInstanceOf(RemoteFailure.class)
                .hasMessage("SERVICE_AUTH_UNAVAILABLE");
    }

    @Test
    void rejectsUserTokensEvenIfTheyContainScope() {
        userToken = true;
        assertThatThrownBy(() -> provider.token("product.stock")).isInstanceOf(RemoteFailure.class);
    }

    @Test
    void rejectsBadSignatureAndScope() {
        wrongSignature = true;
        assertThatThrownBy(() -> provider.token("product.stock")).isInstanceOf(RemoteFailure.class);
        wrongSignature = false;
        wrongScope = true;
        clock.now = clock.now.plusSeconds(6);
        assertThatThrownBy(() -> provider.token("product.stock")).isInstanceOf(RemoteFailure.class);
    }

    @Test
    void rejectsNonLoopbackPlaintextBeforeSendingCredentials() {
        var unsafe =
                new ClientsProperties.Endpoint(true, "account-service", "http://192.0.2.1:1", "");
        var form = new org.springframework.util.LinkedMultiValueMap<String, String>();
        form.add("grant_type", "client_credentials");
        assertThatThrownBy(
                        () ->
                                transport.exchangeServiceCredentials(
                                        unsafe, form, "order-command-service", "synthetic", true))
                .isInstanceOf(RemoteFailure.class)
                .hasMessage("SERVICE_AUTH_TLS_REQUIRED");
        assertThat(requests.get()).isZero();
    }

    @Test
    void disabledAutomaticModePreservesExplicitFixtureToken() {
        var manual =
                new ServiceTokenProviderImpl(
                        transport,
                        clients,
                        new ServiceTokenProperties(
                                false, "", "", null, Duration.ofSeconds(10), false),
                        null,
                        clock);
        assertThat(manual.token("product.stock")).isEqualTo("legacy-fixture");
        assertThat(requests.get()).isZero();
    }

    static class TestClock extends Clock {
        volatile Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);

        public Instant instant() {
            return now;
        }

        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
