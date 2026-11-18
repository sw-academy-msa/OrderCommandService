package com.msa.order.domain.order.query.dto.response;

import com.msa.order.domain.order.entity.*;

import lombok.Builder;

import java.time.Instant;
import java.util.*;

/** 결제 수행에 필요한 Command 원본 HTTP 응답입니다. 주문 목록·일반 상세 Query를 대신하지 않습니다. */
@Builder
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
        ShippingAddressResponse address,
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
        String failureCode,
        String termsVersion) {
    /** 빌더에 담긴 개인정보·결제키·토큰·이벤트 본문을 로그에 노출하지 않습니다. */
    public static class CheckoutResponseBuilder {
        @Override
        public String toString() {
            return "CheckoutResponseBuilder[REDACTED]";
        }
    }

    @Builder
    public record Item(UUID optionId, String label, int quantity) {
        /** 빌더에 담긴 개인정보·결제키·토큰·이벤트 본문을 로그에 노출하지 않습니다. */
        public static class ItemBuilder {
            @Override
            public String toString() {
                return "ItemBuilder[REDACTED]";
            }
        }
    }

    /** JPA 배송지 스냅샷을 복사한 HTTP 응답입니다. 영속 객체와 응답의 변경을 분리합니다. */
    @Builder
    public record ShippingAddressResponse(
            String recipientName,
            String recipientPhone,
            String postalCode,
            String roadAddress,
            String detailAddress,
            String deliveryMemo) {
        /** 빌더에 담긴 개인정보·결제키·토큰·이벤트 본문을 로그에 노출하지 않습니다. */
        public static class ShippingAddressResponseBuilder {
            @Override
            public String toString() {
                return "ShippingAddressResponseBuilder[REDACTED]";
            }
        }

        static ShippingAddressResponse from(ShippingAddress address) {
            return address == null
                    ? null
                    : ShippingAddressResponse.builder()
                            .recipientName(address.recipientName)
                            .recipientPhone(address.recipientPhone)
                            .postalCode(address.postalCode)
                            .roadAddress(address.roadAddress)
                            .detailAddress(address.detailAddress)
                            .deliveryMemo(address.deliveryMemo)
                            .build();
        }

        @Override
        public String toString() {
            return "ShippingAddressResponse[REDACTED]";
        }
    }

    public static CheckoutResponse from(PurchaseOrder o, Instant now) {
        return from(o, now, o.termsVersion);
    }

    /** 결제 전 현재 버전을 전달하고, 동의 후에는 해당 주문이 동의한 버전을 유지합니다. */
    public static CheckoutResponse from(PurchaseOrder o, Instant now, String currentTermsVersion) {
        return CheckoutResponse.builder()
                .orderId(o.id)
                .orderNumber("SD-" + o.id)
                .version(o.version)
                .state(o.state)
                .delivery(o.delivery)
                .productId(o.productId)
                .sellerId(o.sellerId)
                .productName(o.productName)
                .brandOrStore(o.brandOrStore)
                .items(
                        o.items.stream()
                                .map(
                                        i ->
                                                Item.builder()
                                                        .optionId(i.optionId)
                                                        .label(i.label)
                                                        .quantity(i.quantity)
                                                        .build())
                                .toList())
                .address(ShippingAddressResponse.from(o.address))
                .unitPrice(o.unitPrice)
                .shippingFee(o.shippingFee)
                .totalAmount(o.totalAmount)
                .dispatchBusinessDays(o.dispatchBusinessDays)
                .expiresAt(o.expiresAt)
                .serverTime(now)
                .paymentAttemptId(o.paymentAttemptId)
                .confirmedAt(o.confirmedAt)
                .carrier(o.carrier)
                .trackingNumber(o.trackingNumber)
                .termsVersion(o.termsVersion == null ? currentTermsVersion : o.termsVersion)
                .failureCode(o.lastFailureCode)
                .build();
    }

    @Override
    public String toString() {
        return "CheckoutResponse[orderId=" + orderId + ",state=" + state + "]";
    }
}
