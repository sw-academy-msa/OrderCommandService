package com.msa.order.domain.terms.exception;

import com.msa.order.global.error.ErrorCode;

import org.springframework.http.HttpStatus;

/** 없는 이력과 현재 약관 준비 실패를 구분하며 요청값이나 본문을 오류에 넣지 않습니다. */
public enum TermsErrorCode implements ErrorCode {
    NOT_FOUND(HttpStatus.NOT_FOUND, "TERMS_NOT_FOUND", "약관 버전을 찾을 수 없습니다."),
    UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "TERMS_UNAVAILABLE", "현재 결제 약관이 준비되지 않았습니다."),
    INVALID(HttpStatus.BAD_REQUEST, "TERMS_INVALID", "약관 버전이 올바르지 않습니다.");
    private final HttpStatus status;
    private final String code;
    private final String message;

    TermsErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }
}
