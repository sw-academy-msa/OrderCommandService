package com.msa.order.global.config.checkout;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 브라우저가 임의의 약관 버전을 동의 이력에 저장하지 않도록 서버 기준 버전을 지정합니다. */
@Validated
@ConfigurationProperties("order.checkout")
public record CheckoutProperties(@NotBlank String termsVersion) {}
