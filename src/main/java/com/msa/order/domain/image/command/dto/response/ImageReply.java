package com.msa.order.domain.image.command.dto.response;

import java.time.Instant;
import java.util.UUID;

/** 브라우저에 전달할 제한된 업로드/조회 권한입니다. toString으로 서명 URL을 출력하지 않습니다. */
public record ImageReply(
        UUID id, String url, String contentType, long sizeBytes, Instant expiresAt) {
    @Override
    public String toString() {
        return "ImageReply[id=" + id + "]";
    }
}
