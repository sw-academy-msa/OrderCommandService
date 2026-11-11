package com.msa.order.domain.terms.exception;

import com.msa.order.global.error.BusinessException;

/** 기존 공통 오류 처리기로 전달되는 약관 도메인 예외입니다. */
public class TermsException extends BusinessException {
    public TermsException(TermsErrorCode code) {
        super(code);
    }
}
