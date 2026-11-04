package com.msa.order.domain.inquiry.command.dto.response;

import com.msa.order.domain.inquiry.entity.OrderInquiry;

import lombok.Builder;

import java.time.Instant;
import java.util.*;

/** 문의 작성/답변 화면의 HTTP 응답입니다. 사진 조회 권한은 별도 소유권 검증을 거칩니다. */
@Builder
public record InquiryResponse(
        UUID id,
        UUID orderId,
        long version,
        String category,
        List<Message> messages,
        List<UUID> imageIds) {
    /** 빌더에 담긴 개인정보·결제키·토큰·이벤트 본문을 로그에 노출하지 않습니다. */
    public static class InquiryResponseBuilder {
        @Override
        public String toString() {
            return "InquiryResponseBuilder[REDACTED]";
        }
    }

    @Builder
    public record Message(boolean seller, String body, Instant createdAt) {
        /** 빌더에 담긴 개인정보·결제키·토큰·이벤트 본문을 로그에 노출하지 않습니다. */
        public static class MessageBuilder {
            @Override
            public String toString() {
                return "MessageBuilder[REDACTED]";
            }
        }

        @Override
        public String toString() {
            return "Message[REDACTED]";
        }
    }

    public static InquiryResponse from(OrderInquiry i, List<UUID> images) {
        return InquiryResponse.builder()
                .id(i.id)
                .orderId(i.orderId)
                .version(i.version)
                .category(i.category)
                .messages(
                        i.messages.stream()
                                .map(
                                        m ->
                                                Message.builder()
                                                        .seller(m.seller)
                                                        .body(m.body)
                                                        .createdAt(m.createdAt)
                                                        .build())
                                .toList())
                .imageIds(images)
                .build();
    }

    @Override
    public String toString() {
        return "InquiryResponse[id=" + id + "]";
    }
}
