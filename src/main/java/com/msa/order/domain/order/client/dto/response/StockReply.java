package com.msa.order.domain.order.client.dto.response;

import lombok.Builder;

import java.time.Instant;
import java.util.*;

/** Product 내부 HTTP 응답입니다. Query 모델·브라우저 가격을 주문 원본으로 채택하지 않습니다. */
@Builder
public record StockReply(
        UUID id,
        UUID orderId,
        UUID productId,
        UUID buyerId,
        String status,
        Instant expiresAt,
        Instant serverTime,
        long unitPrice,
        long shippingFee,
        Map<UUID, Integer> items,
        UUID sellerId,
        String productName,
        String brandOrStore,
        Integer dispatchBusinessDays,
        Map<UUID, String> optionLabels) {
    /** 빌더에 담긴 개인정보·결제키·토큰·이벤트 본문을 로그에 노출하지 않습니다. */
    public static class StockReplyBuilder {
        @Override
        public String toString() {
            return "StockReplyBuilder[REDACTED]";
        }
    }
}
