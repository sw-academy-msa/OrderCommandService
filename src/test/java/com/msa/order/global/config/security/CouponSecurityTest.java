package com.msa.order.global.config.security;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.msa.order.domain.order.query.controller.CouponOutcomeController;
import com.msa.order.domain.order.query.dto.response.CouponOutcome;
import com.msa.order.domain.order.query.service.CouponOutcomeService;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

/** 실제 서명과 JWKS로 쿠폰 복구 API의 서비스 주체 및 scope 격리를 검증합니다. */
@SpringJUnitConfig(CouponSecurityTest.Config.class)
@WebAppConfiguration
@TestPropertySource(properties = "COUPON_SERVICE_SUBJECT=00000000-0000-0000-0000-000000000088")
class CouponSecurityTest {
    static final RSAKey KEY;
    static final HttpServer JWKS;
    static final String SUBJECT = "00000000-0000-0000-0000-000000000088";

    static {
        try {
            KEY = new RSAKeyGenerator(2048).keyID("coupon-test").generate();
            JWKS = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            JWKS.createContext(
                    "/jwks",
                    e -> {
                        var body =
                                new JWKSet(KEY.toPublicJWK())
                                        .toString()
                                        .getBytes(StandardCharsets.UTF_8);
                        e.getResponseHeaders().set("Content-Type", "application/json");
                        e.sendResponseHeaders(200, body.length);
                        e.getResponseBody().write(body);
                        e.close();
                    });
            JWKS.start();
        } catch (Exception failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    @Autowired WebApplicationContext context;

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({CouponSecurityConfig.class, CouponOutcomeController.class})
    static class Config {
        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        AuthProperties auth() {
            return new AuthProperties(
                    "https://account.test",
                    "stockdrop-api",
                    "http://127.0.0.1:" + JWKS.getAddress().getPort() + "/jwks");
        }

        @Bean
        CouponOutcomeService outcome() {
            return id -> new CouponOutcome(id, "VERIFYING_PAYMENT", true, 3000);
        }
    }

    @AfterAll
    static void stop() {
        JWKS.stop(0);
    }

    @Test
    void onlyCouponServiceWithReadScopeCanInspectAndResponsesAreNotCached() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String path = "/internal/v1/orders/" + UUID.randomUUID() + "/coupon-outcome";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        for (String token :
                List.of(
                        token("coupon-service", "service", "wrong", SUBJECT),
                        token("order-command-service", "service", "order.coupon.read", SUBJECT),
                        token("coupon-service", "user", "order.coupon.read", SUBJECT),
                        token(
                                "coupon-service",
                                "service",
                                "order.coupon.read",
                                UUID.randomUUID().toString()))) {
            mvc.perform(get(path).header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(
                        get(path)
                                .header(
                                        "Authorization",
                                        "Bearer "
                                                + token(
                                                        "coupon-service",
                                                        "service",
                                                        "order.coupon.read",
                                                        SUBJECT)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.phase").value("VERIFYING_PAYMENT"))
                .andExpect(jsonPath("$.data.couponApplied").value(true));
    }

    private String token(String client, String principal, String scope, String subject)
            throws Exception {
        var claims =
                new JWTClaimsSet.Builder()
                        .issuer("https://account.test")
                        .audience("stockdrop-api")
                        .subject(subject)
                        .issueTime(new Date())
                        .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                        .claim("client_id", client)
                        .claim("principal_type", principal)
                        .claim("scope", scope)
                        .claim("token_use", "access")
                        .claim("roles", List.of())
                        .build();
        var jwt =
                new SignedJWT(
                        new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY.getKeyID()).build(),
                        claims);
        jwt.sign(new RSASSASigner(KEY));
        return jwt.serialize();
    }
}
