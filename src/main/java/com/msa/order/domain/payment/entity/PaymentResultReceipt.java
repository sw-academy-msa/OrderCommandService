package com.msa.order.domain.payment.entity;

import jakarta.persistence.*;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/** 이벤트 ID와 내용의 동일성을 보장하는 소비 이력입니다. 결과 반영과 같은 트랜잭션으로 INSERT합니다. */
@Entity
@Table(name = "payment_result_receipts", schema = "orders")
public class PaymentResultReceipt {
    /** 빌더에 담긴 개인정보·결제키·토큰·이벤트 본문을 로그에 노출하지 않습니다. */
    public static class PaymentResultReceiptBuilder {
        @Override
        public String toString() {
            return "PaymentResultReceiptBuilder[REDACTED]";
        }
    }

    @Id public UUID id;

    @Column(nullable = false, length = 64)
    public String payloadHash;

    @Column(nullable = false)
    public Instant processedAt;

    protected PaymentResultReceipt() {}

    // 기존 생성자를 거쳐 UUID·초기 상태·시간과 컬렉션 초기화를 보존하며 JPA 기본 생성자도 유지합니다.
    @Builder
    public PaymentResultReceipt(UUID id, String hash, Instant now) {
        this.id = id;
        payloadHash = hash;
        processedAt = now;
    }
}
