package com.msa.order.domain.terms.query.controller;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.msa.order.domain.order.entity.PurchaseOrder;
import com.msa.order.domain.order.query.service.OrderStatusQueryService;
import com.msa.order.domain.order.repository.OrderRepository;
import com.msa.order.domain.terms.exception.TermsException;
import com.msa.order.domain.terms.query.service.*;
import com.msa.order.domain.terms.query.service.impl.CheckoutTermsServiceImpl;
import com.msa.order.domain.terms.repository.CheckoutTermsRepository;
import com.msa.order.global.config.checkout.CheckoutProperties;
import com.msa.order.global.config.database.OrderDatabaseTestSupport;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

/** 실제 Flyway seed와 JPA 조회, 역할, 약관 불변성 및 checkout 버전 전달을 격리 DB에서 검증합니다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(OrderDatabaseTestSupport.Time.class)
class CheckoutTermsControllerTest extends OrderDatabaseTestSupport {
    @Autowired MockMvc mvc;
    @Autowired CheckoutTermsService terms;
    @Autowired CheckoutTermsRepository repository;
    @Autowired OrderRepository orders;
    @Autowired OrderStatusQueryService queries;
    @Autowired MutableClock clock;
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;

    @BeforeEach
    void setup() {
        clock.set(Instant.parse("2030-01-01T00:00:00Z"));
    }

    @Test
    void readsSeededCurrentAndHistoricalTermsWithCommonEnvelope() throws Exception {
        var buyer = jwt().authorities(new SimpleGrantedAuthority("ROLE_BUYER"));
        mvc.perform(get("/api/v1/orders/checkout-terms").with(buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.version").value("checkout-v1"))
                .andExpect(jsonPath("$.data.content").isNotEmpty())
                .andExpect(jsonPath("$.data.effectiveAt").exists());
        mvc.perform(get("/api/v1/orders/checkout-terms/checkout-v1").with(buyer))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/orders/checkout-terms/missing").with(buyer))
                .andExpect(status().isNotFound());
    }

    @Test
    void anonymousAndSellerCannotReadBuyerTerms() throws Exception {
        mvc.perform(get("/api/v1/orders/checkout-terms")).andExpect(status().isUnauthorized());
        mvc.perform(
                        get("/api/v1/orders/checkout-terms")
                                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SELLER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void seedChecksumMatchesUtf8ContentAndFlywayRunsExactlyOnce() throws Exception {
        var v = terms.current();
        assertThat(v.contentSha256())
                .isEqualTo(
                        HexFormat.of()
                                .formatHex(
                                        MessageDigest.getInstance("SHA-256")
                                                .digest(
                                                        v.content()
                                                                .getBytes(
                                                                        StandardCharsets.UTF_8))));
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from orders.checkout_terms where"
                                        + " version='checkout-v1'",
                                Integer.class))
                .isEqualTo(1);
    }

    @Test
    void existingVersionCannotBeOverwrittenOrDeleted() {
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "update orders.checkout_terms set content='changed' where"
                                                + " version='checkout-v1'"))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "delete from orders.checkout_terms where"
                                                + " version='checkout-v1'"))
                .isInstanceOf(RuntimeException.class);
        assertThat(terms.current().content()).contains("5분");
    }

    @Test
    void missingAndNotYetEffectiveCurrentVersionsFailClosedButOldVersionRemainsReadable() {
        var missing =
                new CheckoutTermsServiceImpl(repository, new CheckoutProperties("missing"), clock);
        assertThatThrownBy(missing::current).isInstanceOf(TermsException.class);
        clock.set(Instant.parse("2026-01-01T00:00:00Z"));
        assertThatThrownBy(terms::current).isInstanceOf(TermsException.class);
        assertThat(terms.version("checkout-v1").version()).isEqualTo("checkout-v1");
    }

    @Test
    void checkoutReturnsCurrentVersionBeforeConsentAndRetainsConsentedVersionAfterward() {
        UUID buyer = UUID.randomUUID();
        var o =
                PurchaseOrder.builder()
                        .buyer(buyer)
                        .key(UUID.randomUUID())
                        .hash("synthetic")
                        .product(UUID.randomUUID())
                        .now(clock.instant())
                        .build();
        o = orders.saveAndFlush(o);
        assertThat(queries.buyer(buyer, o.id).termsVersion()).isEqualTo("checkout-v1");
        o.termsVersion = "historical-fixture";
        orders.saveAndFlush(o);
        assertThat(queries.buyer(buyer, o.id).termsVersion()).isEqualTo("historical-fixture");
    }
}
