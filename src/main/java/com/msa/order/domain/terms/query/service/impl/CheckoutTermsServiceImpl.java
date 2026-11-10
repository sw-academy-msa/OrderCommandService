package com.msa.order.domain.terms.query.service.impl;

import com.msa.order.domain.terms.entity.CheckoutTerms;
import com.msa.order.domain.terms.exception.*;
import com.msa.order.domain.terms.query.dto.response.CheckoutTermsResponse;
import com.msa.order.domain.terms.query.service.CheckoutTermsService;
import com.msa.order.domain.terms.repository.CheckoutTermsRepository;
import com.msa.order.global.config.checkout.CheckoutProperties;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/** 현재 버전은 기존 서버 설정을 재사용합니다. DB 누락·미래 시행 약관으로 결제를 받지 않습니다. */
@Service
@Transactional(readOnly = true)
public class CheckoutTermsServiceImpl implements CheckoutTermsService {
    private final CheckoutTermsRepository terms;
    private final CheckoutProperties settings;
    private final Clock clock;

    public CheckoutTermsServiceImpl(
            CheckoutTermsRepository terms, CheckoutProperties settings, Clock clock) {
        this.terms = terms;
        this.settings = settings;
        this.clock = clock;
    }

    public CheckoutTermsResponse current() {
        var t =
                terms.findVersion(settings.termsVersion())
                        .orElseThrow(() -> new TermsException(TermsErrorCode.UNAVAILABLE));
        if (t.getEffectiveAt().isAfter(clock.instant()))
            throw new TermsException(TermsErrorCode.UNAVAILABLE);
        return response(t);
    }

    public CheckoutTermsResponse version(String version) {
        if (version == null || !version.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,99}"))
            throw new TermsException(TermsErrorCode.INVALID);
        var t =
                terms.findVersion(version)
                        .orElseThrow(() -> new TermsException(TermsErrorCode.NOT_FOUND));
        return response(t);
    }

    private CheckoutTermsResponse response(CheckoutTerms t) {
        return CheckoutTermsResponse.builder()
                .version(t.getVersion())
                .title(t.getTitle())
                .content(t.getContent())
                .effectiveAt(t.getEffectiveAt())
                .contentSha256(t.getContentSha256())
                .build();
    }
}
