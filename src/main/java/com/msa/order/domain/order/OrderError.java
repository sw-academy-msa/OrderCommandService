package com.msa.order.domain.order;

import com.msa.order.global.error.ErrorCode;

import org.springframework.http.HttpStatus;

/** 내부 HTTP 응답 원문·배송 개인정보를 노출하지 않는 주문 오류입니다. */
public enum OrderError implements ErrorCode {
    NOT_FOUND(HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다."),
    OWNER(HttpStatus.FORBIDDEN, "본인 주문만 처리할 수 있습니다."),
    STATE(HttpStatus.CONFLICT, "현재 주문 상태에서는 처리할 수 없습니다."),
    DUPLICATE(HttpStatus.CONFLICT, "같은 요청 키의 주문 내용이 다릅니다."),
    INVALID(HttpStatus.BAD_REQUEST, "주문 입력값이 올바르지 않습니다."),
    UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "외부 연동 설정이나 응답이 준비되지 않았습니다.");
    private final HttpStatus status;
    private final String message;

    OrderError(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return "ORDER_" + name();
    }

    public String message() {
        return message;
    }
}
