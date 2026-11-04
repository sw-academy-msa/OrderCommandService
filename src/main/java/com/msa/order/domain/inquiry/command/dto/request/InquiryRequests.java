package com.msa.order.domain.inquiry.command.dto.request;

import jakarta.validation.constraints.*;

import java.util.*;

/** 반품 접수와 구분된 문의 입력입니다. 첨부는 이 주문 구매자가 검증 완료한 이미지 ID만 받습니다. */
public final class InquiryRequests {
    private InquiryRequests() {}

    public record Create(
            @NotNull UUID requestKey,
            @NotBlank @Size(max = 100) String category,
            @NotBlank @Size(max = 1000) String body,
            @NotNull @Size(max = 5) List<@NotNull UUID> imageIds) {
        @Override
        public String toString() {
            return "CreateInquiry[REDACTED]";
        }
    }

    public record Message(@NotNull UUID requestKey, @NotBlank @Size(max = 1000) String body) {
        @Override
        public String toString() {
            return "InquiryMessage[REDACTED]";
        }
    }
}
