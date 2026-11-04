package com.msa.order.domain.inquiry.exception;

import com.msa.order.global.error.ErrorCode;

import org.springframework.http.HttpStatus;

/** 주문 문의 작성·판매자 답변 오류입니다. 기존 ORDER_* 응답 코드·상태·메시지를 유지하며 개인정보는 포함하지 않습니다. */
public enum InquiryErrorCode implements ErrorCode {
    NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "주문을 찾을 수 없습니다."),
    OWNER(HttpStatus.FORBIDDEN, "ORDER_OWNER", "본인 주문만 처리할 수 있습니다."),
    STATE(HttpStatus.CONFLICT, "ORDER_STATE", "현재 주문 상태에서는 처리할 수 없습니다."),
    DUPLICATE(HttpStatus.CONFLICT, "ORDER_DUPLICATE", "같은 요청 키의 주문 내용이 다릅니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    InquiryErrorCode(HttpStatus status, String code, String message) {
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
