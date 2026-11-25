package com.msa.order.domain.order.query.controller;

import com.msa.order.domain.order.query.dto.response.CouponOutcome;
import com.msa.order.domain.order.query.service.CouponOutcomeService;
import com.msa.order.global.response.ApiResponse;
import com.msa.order.global.response.SuccessCode;

import io.swagger.v3.oas.annotations.Hidden;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Gateway에 공개하지 않는 쿠폰 서비스 전용 잠금 복구 API입니다. */
@Hidden
@RestController
@RequestMapping(value = "/internal/v1/orders", produces = "application/json")
public class CouponOutcomeController {
    private final CouponOutcomeService service;

    public CouponOutcomeController(CouponOutcomeService service) {
        this.service = service;
    }

    @GetMapping("/{orderId}/coupon-outcome")
    public ResponseEntity<ApiResponse<CouponOutcome>> find(@PathVariable UUID orderId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.onSuccess(service.find(orderId), SuccessCode.OK));
    }
}
