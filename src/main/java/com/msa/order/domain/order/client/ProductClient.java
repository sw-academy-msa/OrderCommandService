package com.msa.order.domain.order.client;

import com.msa.order.domain.order.client.dto.response.StockReply;

import java.time.Instant;
import java.util.*;

/** Product 원본 재고만 판단 근거로 사용합니다. 요청은 Order ID와 결제 시도 ID로 멱등 처리됩니다. */
public interface ProductClient {
    StockReply reserve(UUID order, UUID product, UUID buyer, Map<UUID, Integer> items);

    StockReply get(UUID reservation);

    StockReply begin(UUID reservation, UUID attempt);

    StockReply confirm(UUID reservation, UUID attempt, Instant approvedAt);

    StockReply release(UUID reservation, UUID attempt, boolean failureConfirmed);
}
