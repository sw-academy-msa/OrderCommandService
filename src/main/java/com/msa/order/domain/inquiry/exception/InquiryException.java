package com.msa.order.domain.inquiry.exception;

import com.msa.order.global.error.BusinessException;

/** 주문 문의 작성·판매자 답변 실패를 공통 처리기의 기존 HTTP 오류 응답으로 전달합니다. */
public class InquiryException extends BusinessException {
    public InquiryException(InquiryErrorCode errorCode) {
        super(errorCode);
    }
}
