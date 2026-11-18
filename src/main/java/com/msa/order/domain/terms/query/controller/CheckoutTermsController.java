package com.msa.order.domain.terms.query.controller;

import com.msa.order.domain.terms.query.dto.response.CheckoutTermsResponse;
import com.msa.order.domain.terms.query.service.CheckoutTermsService;
import com.msa.order.global.response.*;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** 결제 화면의 BUYER에게 현재 및 과거 약관을 제공합니다. 별도 관리자 편집 API는 만들지 않습니다. */
@RestController
@RequestMapping(value = "/api/v1/orders/checkout-terms", produces = "application/json")
@PreAuthorize("hasRole('BUYER')")
public class CheckoutTermsController {
    private final CheckoutTermsService service;

    public CheckoutTermsController(CheckoutTermsService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<CheckoutTermsResponse> current() {
        CheckoutTermsResponse response = service.current();
        return ApiResponse.onSuccess(response, SuccessCode.OK);
    }

    @GetMapping("/{version}")
    public ApiResponse<CheckoutTermsResponse> version(@PathVariable String version) {
        CheckoutTermsResponse response = service.version(version);
        return ApiResponse.onSuccess(response, SuccessCode.OK);
    }
}
