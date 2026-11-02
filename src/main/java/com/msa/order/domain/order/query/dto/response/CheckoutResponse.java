package com.msa.order.domain.order.query.dto.response;

import com.msa.order.domain.order.entity.*;

import java.time.Instant;
import java.util.*;

/** 결제 수행에 필요한 Command 원본 HTTP 응답입니다. 주문 목록·일반 상세 Query를 대신하지 않습니다. */
public record CheckoutResponse(
        UUID orderId,
        String orderNumber,
        long version,
        PurchaseOrder.State state,
        PurchaseOrder.Delivery delivery,
        UUID productId,
        UUID sellerId,
        String productName,
        String brandOrStore,
        List<Item> items,
        ShippingAddress address,
        Long unitPrice,
        Long shippingFee,
        Long totalAmount,
        Integer dispatchBusinessDays,
        Instant expiresAt,
        Instant serverTime,
        UUID paymentAttemptId,
        Instant confirmedAt,
        String carrier,
        String trackingNumber,
        String failureCode) {
    public record Item(UUID optionId, String label, int quantity) {}

    public static CheckoutResponse from(PurchaseOrder o, Instant now) {
        return new CheckoutResponse(
                o.id,
                "SD-" + o.id,
                o.version,
                o.state,
                o.delivery,
                o.productId,
                o.sellerId,
                o.productName,
                o.brandOrStore,
                o.items.stream().map(i -> new Item(i.optionId, i.label, i.quantity)).toList(),
                o.address,
                o.unitPrice,
                o.shippingFee,
                o.totalAmount,
                o.dispatchBusinessDays,
                o.expiresAt,
                now,
                o.paymentAttemptId,
                o.confirmedAt,
                o.carrier,
                o.trackingNumber,
                o.lastFailureCode);
    }

    @Override
    public String toString() {
        return "CheckoutResponse[orderId=" + orderId + ",state=" + state + "]";
    }
}
