package com.msa.order.domain.order.entity;

import jakarta.persistence.*;

/** Account 주소록과 독립된 주문 배송지 스냅샷입니다. 변경·삭제된 주소록을 과거 주문에 소급하지 않습니다. */
@Embeddable
public class ShippingAddress {
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
