package com.msa.order.domain.payment.exception;

import com.msa.order.global.error.ErrorCode;

import org.springframework.http.HttpStatus;

/** Order가 수신하는 결제 결과 이벤트의 내부 오류입니다. 실제 결제 원본과 공급자 호출은 Payment 소유입니다. */
public enum PaymentResultErrorCode implements ErrorCode {
    INVALID_EVENT(HttpStatus.BAD_REQUEST, "ORDER_PAYMENT_INVALID_EVENT", "결제 이벤트 계약이 올바르지 않습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    PaymentResultErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
