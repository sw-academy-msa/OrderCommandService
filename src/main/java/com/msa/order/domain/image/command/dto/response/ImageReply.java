package com.msa.order.domain.image.command.dto.response;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/** 브라우저에 전달할 제한된 업로드/조회 권한입니다. toString으로 서명 URL을 출력하지 않습니다. */
@Builder
public record ImageReply(
        UUID id, String url, String contentType, long sizeBytes, Instant expiresAt) {
    /** 빌더에 담긴 개인정보·결제키·토큰·이벤트 본문을 로그에 노출하지 않습니다. */
    public static class ImageReplyBuilder {
        @Override
        public String toString() {
            return "ImageReplyBuilder[REDACTED]";
        }
    }

    @Override
    public String toString() {
        return "ImageReply[id=" + id + "]";
    }
}
