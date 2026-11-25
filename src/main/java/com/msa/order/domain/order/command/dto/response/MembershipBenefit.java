package com.msa.order.domain.order.command.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

/** 검증된 사용자 JWT의 회원 자격만 전달하며 토큰 원문은 보관하지 않습니다. */
public record MembershipBenefit(boolean active, Instant validUntil, long version) {
    public static MembershipBenefit none() {
        return new MembershipBenefit(false, Instant.EPOCH, 0);
    }

    public static MembershipBenefit from(Object claim) {
        if (!(claim instanceof Map<?, ?> values)
                || !Boolean.TRUE.equals(values.get("active"))
                || !(values.get("state") instanceof String state)
                || !Set.of("ACTIVE", "GRACE", "CANCEL_SCHEDULED").contains(state)) return none();
        try {
            if (!(values.get("valid_until") instanceof Number until)
                    || !(values.get("version") instanceof Number version)) return none();
            long epoch = new BigDecimal(until.toString()).longValueExact();
            long revision = new BigDecimal(version.toString()).longValueExact();
            if (epoch <= 0 || revision < 1) return none();
            return new MembershipBenefit(true, Instant.ofEpochSecond(epoch), revision);
        } catch (RuntimeException invalid) {
            return none();
        }
    }
}
