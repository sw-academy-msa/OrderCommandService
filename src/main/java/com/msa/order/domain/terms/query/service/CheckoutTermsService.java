package com.msa.order.domain.terms.query.service;

import com.msa.order.domain.terms.query.dto.response.CheckoutTermsResponse;

/** 현재 결제 약관과 과거 동의 버전의 본문을 동일한 원본에서 제공합니다. */
public interface CheckoutTermsService {
    CheckoutTermsResponse current();

    CheckoutTermsResponse version(String version);
}
