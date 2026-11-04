package com.msa.order.domain.order.exception;

import com.msa.order.global.error.BusinessException;

/** 주문 생성·결제 시작·배송 처리·업무 상태 조회 실패를 공통 처리기의 기존 HTTP 오류 응답으로 전달합니다. */
public class OrderException extends BusinessException {
    public OrderException(OrderErrorCode errorCode) {
        super(errorCode);
    }
}
