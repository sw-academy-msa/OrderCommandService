package com.msa.order.domain.order.client.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;

import java.time.Instant;

/** Account 내부 발급 응답입니다. 서명 검증을 통과하기 전에는 캐시에 넣지 않습니다. */
@Builder
public record ServiceTokenReply(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresIn,
        @JsonProperty("expires_at") Instant expiresAt,
        String scope) {
    /** 빌더에 담긴 개인정보·결제키·토큰·이벤트 본문을 로그에 노출하지 않습니다. */
    public static class ServiceTokenReplyBuilder {
        @Override
        public String toString() {
            return "ServiceTokenReplyBuilder[REDACTED]";
        }
    }

    @Override
    public String toString() {
        return "ServiceTokenReply[REDACTED]";
    }
}
