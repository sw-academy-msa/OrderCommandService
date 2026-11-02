package com.msa.order.domain.order.client.dto.response;

import java.time.Instant;
import java.util.*;

/** Product 내부 HTTP 응답입니다. Query 모델·브라우저 가격을 주문 원본으로 채택하지 않습니다. */
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
        Map<UUID, String> optionLabels) {}
