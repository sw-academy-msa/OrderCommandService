package com.msa.order.domain.image.exception;

import com.msa.order.global.error.BusinessException;

/** 주문 문의 이미지 업로드·검증·접근 실패를 공통 처리기의 기존 HTTP 오류 응답으로 전달합니다. */
public class ImageException extends BusinessException {
    public ImageException(ImageErrorCode errorCode) {
        super(errorCode);
    }
}
