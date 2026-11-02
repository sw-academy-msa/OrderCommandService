package com.msa.order.domain.inquiry.command.dto.response;

import com.msa.order.domain.inquiry.entity.OrderInquiry;

import java.time.Instant;
import java.util.*;

/** 문의 작성/답변 화면의 HTTP 응답입니다. 사진 조회 권한은 별도 소유권 검증을 거칩니다. */
public record InquiryResponse(
        UUID id,
        UUID orderId,
        long version,
        String category,
        List<Message> messages,
        List<UUID> imageIds) {
    public record Message(boolean seller, String body, Instant createdAt) {
        @Override
        public String toString() {
            return "Message[REDACTED]";
        }
    }

    public static InquiryResponse from(OrderInquiry i, List<UUID> images) {
        return new InquiryResponse(
                i.id,
                i.orderId,
                i.version,
                i.category,
                i.messages.stream().map(m -> new Message(m.seller, m.body, m.createdAt)).toList(),
                images);
    }

    @Override
    public String toString() {
        return "InquiryResponse[id=" + id + "]";
    }
}
