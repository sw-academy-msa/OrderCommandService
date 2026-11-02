package com.msa.order.domain.order.client.dto.response;

import com.msa.order.domain.order.entity.ShippingAddress;

import java.time.Instant;
import java.util.UUID;

/** 실제 Account 본인 배송지 API 응답입니다. 원본 주소록 ID 소유권은 Account도 검증합니다. */
public record AddressReply(
        UUID addressId,
        String label,
        String recipientName,
        String recipientPhone,
        String postalCode,
        String roadAddress,
        String detailAddress,
        String deliveryMemo,
        boolean defaultAddress,
        Instant createdAt,
        Instant updatedAt) {
    public ShippingAddress snapshot() {
        if (addressId == null
                || recipientName == null
                || recipientName.isBlank()
                || recipientName.length() > 100
                || recipientPhone == null
                || recipientPhone.length() > 30
                || postalCode == null
                || postalCode.length() > 20
                || roadAddress == null
                || roadAddress.isBlank()
                || roadAddress.length() > 500
                || detailAddress != null && detailAddress.length() > 500
                || deliveryMemo != null && deliveryMemo.length() > 1000) {
            throw new com.msa.order.domain.order.client.RemoteFailure(
                    "INVALID_ADDRESS_RESPONSE", false);
        }
        return new ShippingAddress(
                recipientName,
                recipientPhone,
                postalCode,
                roadAddress,
                detailAddress,
                deliveryMemo);
    }

    @Override
    public String toString() {
        return "AddressReply[REDACTED]";
    }
}
