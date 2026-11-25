package com.msa.order.global.config.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;

import java.time.*;
import java.util.*;

/** 쿠폰 서비스 주체와 읽기 scope만 허용하는 전용 인증 체인입니다. */
@Configuration
public class CouponSecurityConfig {
    @Bean
    @Order(-1)
    public SecurityFilterChain couponSecurity(
            HttpSecurity http,
            AuthProperties auth,
            Clock clock,
            @Value("${COUPON_SERVICE_SUBJECT:}") String subject)
            throws Exception {
        var decoder =
                NimbusJwtDecoder.withJwkSetUri(auth.jwkSetUri())
                        .jwsAlgorithm(SignatureAlgorithm.RS256)
                        .build();
        var time = new JwtTimestampValidator(Duration.ofSeconds(30));
        time.setClock(clock);
        OAuth2TokenValidator<Jwt> contract =
                jwt -> {
                    String scope = jwt.getClaimAsString("scope");
                    boolean allowed =
                            !subject.isBlank()
                                    && subject.equals(jwt.getSubject())
                                    && "coupon-service".equals(jwt.getClaimAsString("client_id"))
                                    && "service".equals(jwt.getClaimAsString("principal_type"))
                                    && jwt.getExpiresAt() != null
                                    && "access".equals(jwt.getClaimAsString("token_use"))
                                    && jwt.getAudience() != null
                                    && jwt.getAudience().contains(auth.audience())
                                    && scope != null
                                    && Arrays.asList(scope.split(" +"))
                                            .contains("order.coupon.read");
                    return allowed
                            ? OAuth2TokenValidatorResult.success()
                            : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
                };
        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        time, new JwtIssuerValidator(auth.issuer()), contract));
        return http.securityMatcher("/internal/v1/orders/*/coupon-outcome")
                .csrf(c -> c.disable())
                .cors(c -> c.disable())
                .requestCache(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(c -> c.disable())
                .httpBasic(c -> c.disable())
                .logout(c -> c.disable())
                .authorizeHttpRequests(a -> a.anyRequest().authenticated())
                .oauth2ResourceServer(o -> o.jwt(j -> j.decoder(decoder)))
                .build();
    }
}
