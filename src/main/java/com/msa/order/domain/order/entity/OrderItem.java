package com.msa.order.domain.order.entity;

import jakarta.persistence.*;

import java.util.UUID;

/** 같은 상품 안의 옵션별 주문 스냅샷입니다. 상품·판매자를 혼합하는 장바구니는 만들지 않습니다. */
@Embeddable
public class OrderItem {
    @Column(nullable = false)
    public UUID optionId;

    @Column(nullable = false)
    public int quantity;

    @Column(length = 500)
    public String label;

    protected OrderItem() {}

    public OrderItem(UUID id, int quantity) {
        this.optionId = id;
        this.quantity = quantity;
    }
}
