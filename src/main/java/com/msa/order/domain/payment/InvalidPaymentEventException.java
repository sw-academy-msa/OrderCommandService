package com.msa.order.domain.payment;

/** 영구 계약 오류만 DLT로 분리합니다. 이벤트/결제키/오류 원문은 예외에 포함하지 않습니다. */
public class InvalidPaymentEventException extends RuntimeException {
    public InvalidPaymentEventException() {
        super("결제 이벤트 계약이 올바르지 않습니다.");
    }
}
