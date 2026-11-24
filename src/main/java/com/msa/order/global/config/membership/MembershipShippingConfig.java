package com.msa.order.global.config.membership;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 기존 설정 빈 등록 방식으로 멤버십 배송비 정책을 구성합니다. */
@Configuration
@EnableConfigurationProperties(MembershipShippingProperties.class)
public class MembershipShippingConfig {}
