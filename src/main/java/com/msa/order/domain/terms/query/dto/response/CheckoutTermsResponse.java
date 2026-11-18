package com.msa.order.domain.terms.query.dto.response;

import lombok.Builder;

import java.time.Instant;

/** 화면에 표시할 평문 약관 HTTP 응답입니다. 엔티티를 직접 노출하지 않습니다. */
@Builder
public record CheckoutTermsResponse(
        String version, String title, String content, Instant effectiveAt, String contentSha256) {}
