package com.msa.order.domain.payment;

import com.msa.order.domain.payment.exception.PaymentResultErrorCode;
import com.msa.order.domain.payment.exception.PaymentResultException;

/**
 * 영구 계약 오류만 DLT로 분리합니다. 이벤트·결제키·오류 원문은 예외에 포함하지 않습니다. 기존 타입·패키지·메시지는 Kafka 재시도 분류와 DLT 예외 헤더의 호환성을
 * 위해 유지합니다.
 */
public class InvalidPaymentEventException extends PaymentResultException {
    public InvalidPaymentEventException() {
        super(PaymentResultErrorCode.INVALID_EVENT);
    }
}
