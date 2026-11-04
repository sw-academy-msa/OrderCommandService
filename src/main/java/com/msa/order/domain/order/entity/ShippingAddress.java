package com.msa.order.domain.order.entity;

import jakarta.persistence.*;

import lombok.Builder;

/** Account 주소록과 독립된 주문 배송지 스냅샷입니다. 변경·삭제된 주소록을 과거 주문에 소급하지 않습니다. */
@Embeddable
public class ShippingAddress {
    /** 빌더에 담긴 개인정보·결제키·토큰·이벤트 본문을 로그에 노출하지 않습니다. */
    public static class ShippingAddressBuilder {
        @Override
        public String toString() {
            return "ShippingAddressBuilder[REDACTED]";
        }
    }

    @Column(length = 100)
    public String recipientName;

    @Column(length = 30)
    public String recipientPhone;

    @Column(length = 20)
    public String postalCode;

    @Column(length = 500)
    public String roadAddress;

    @Column(length = 500)
    public String detailAddress;

    @Column(length = 1000)
    public String deliveryMemo;

    protected ShippingAddress() {}

    // 기존 생성자를 거쳐 UUID·초기 상태·시간과 컬렉션 초기화를 보존하며 JPA 기본 생성자도 유지합니다.
    @Builder
    public ShippingAddress(
            String name, String phone, String postal, String road, String detail, String memo) {
        this.recipientName = name;
        this.recipientPhone = phone;
        this.postalCode = postal;
        this.roadAddress = road;
        this.detailAddress = detail;
        this.deliveryMemo = memo;
    }

    @Override
    public String toString() {
        return "ShippingAddress[REDACTED]";
    }
}
