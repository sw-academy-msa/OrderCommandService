package com.msa.order.domain.order.client.dto.response;

import com.msa.order.domain.order.entity.ShippingAddress;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/** 실제 Account 본인 배송지 API 응답입니다. 원본 주소록 ID 소유권은 Account도 검증합니다. */
@Builder
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
    /** 빌더에 담긴 개인정보·결제키·토큰·이벤트 본문을 로그에 노출하지 않습니다. */
    public static class AddressReplyBuilder {
        @Override
        public String toString() {
            return "AddressReplyBuilder[REDACTED]";
        }
    }

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
        return ShippingAddress.builder()
                .name(recipientName)
                .phone(recipientPhone)
                .postal(postalCode)
                .road(roadAddress)
                .detail(detailAddress)
                .memo(deliveryMemo)
                .build();
    }

    @Override
    public String toString() {
        return "AddressReply[REDACTED]";
    }
}
