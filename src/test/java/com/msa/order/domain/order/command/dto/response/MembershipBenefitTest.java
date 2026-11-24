package com.msa.order.domain.order.command.dto.response;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

/** 회원 claim의 누락과 타입 위조가 배송비 지원으로 이어지지 않는지 검증합니다. */
class MembershipBenefitTest {
    @Test
    void readsOnlyCompleteUserMembershipContract() {
        var benefit =
                MembershipBenefit.from(
                        Map.of(
                                "active",
                                true,
                                "state",
                                "GRACE",
                                "valid_until",
                                2000000000L,
                                "version",
                                3L));
        assertThat(benefit.active()).isTrue();
        assertThat(benefit.validUntil()).isEqualTo(Instant.ofEpochSecond(2000000000L));
        for (Object invalid :
                new Object[] {
                    "true",
                    Map.of(),
                    Map.of("active", true),
                    Map.of(
                            "active",
                            true,
                            "state",
                            "EXPIRED",
                            "valid_until",
                            2000000000L,
                            "version",
                            3L),
                    Map.of(
                            "active",
                            "true",
                            "state",
                            "ACTIVE",
                            "valid_until",
                            2000000000L,
                            "version",
                            3L),
                    Map.of("active", true, "state", "ACTIVE", "valid_until", 2.5, "version", 3L)
                }) {
            assertThat(MembershipBenefit.from(invalid).active()).isFalse();
        }
        assertThat(MembershipBenefit.from(null).active()).isFalse();
    }
}
