package com.msa.order.global.config.membership;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 배송비 정책은 주문에 복사하여 이후 설정 변경으로 금액이 바뀌지 않게 합니다. */
@ConfigurationProperties("membership.shipping")
public record MembershipShippingProperties(
        boolean enabled, String mode, Integer rateBps, Long amount, String policyVersion) {
    public MembershipShippingProperties {
        if (mode == null) mode = "RATE";
        if (rateBps == null) rateBps = 10000;
        if (amount == null) amount = 0L;
        if (policyVersion == null) policyVersion = "membership-shipping-v1";
        if (!java.util.Set.of("RATE", "AMOUNT").contains(mode)
                || rateBps < 0
                || rateBps > 10000
                || amount < 0
                || policyVersion.isBlank()
                || policyVersion.length() > 40)
            throw new IllegalArgumentException("배송비 할인 정책 설정이 올바르지 않습니다.");
    }
}
