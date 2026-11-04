package com.msa.order.domain.order.entity;

import jakarta.persistence.*;

import lombok.Builder;

import java.util.UUID;

/** 같은 상품 안의 옵션별 주문 스냅샷입니다. 상품·판매자를 혼합하는 장바구니는 만들지 않습니다. */
@Embeddable
public class OrderItem {
    /** 빌더에 담긴 개인정보·결제키·토큰·이벤트 본문을 로그에 노출하지 않습니다. */
    public static class OrderItemBuilder {
        @Override
        public String toString() {
            return "OrderItemBuilder[REDACTED]";
        }
    }

    @Column(nullable = false)
    public UUID optionId;

    @Column(nullable = false)
    public int quantity;

    @Column(length = 500)
    public String label;

    protected OrderItem() {}

    // 기존 생성자를 거쳐 UUID·초기 상태·시간과 컬렉션 초기화를 보존하며 JPA 기본 생성자도 유지합니다.
    @Builder
    public OrderItem(UUID id, int quantity) {
        this.optionId = id;
        this.quantity = quantity;
    }
}
