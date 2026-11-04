package com.msa.order.domain.payment.exception;

import com.msa.order.global.error.BusinessException;

/** 결제 결과 반영의 업무 예외 기반 타입입니다. 기반 타입 전체를 Kafka 재시도 제외 대상으로 등록하지 않습니다. */
public class PaymentResultException extends BusinessException {
    public PaymentResultException(PaymentResultErrorCode errorCode) {
        super(errorCode);
    }
}
