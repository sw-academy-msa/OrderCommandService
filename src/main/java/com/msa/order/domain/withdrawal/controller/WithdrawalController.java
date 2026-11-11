package com.msa.order.domain.withdrawal.controller;

import com.msa.order.domain.withdrawal.dto.response.WithdrawalResponses.*;
import com.msa.order.domain.withdrawal.service.*;
import com.msa.order.global.response.*;

import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** 외부 Gateway에 공개하지 않는 Account 전용 확인과 영속 차단 API입니다. */
@io.swagger.v3.oas.annotations.Hidden
@RestController
@RequestMapping(value = "/internal/v1/account-withdrawals/{account}", produces = "application/json")
public class WithdrawalController {
    private final WithdrawalGateService gates;
    private final WithdrawalStateService state;

    public WithdrawalController(WithdrawalGateService gates, WithdrawalStateService state) {
        this.gates = gates;
        this.state = state;
    }

    @GetMapping
    public ApiResponse<State> inspect(@PathVariable UUID account) {
        return ApiResponse.onSuccess(state.inspect(account), SuccessCode.OK);
    }

    @PutMapping("/fences/{operation}")
    public ApiResponse<State> acquire(@PathVariable UUID account, @PathVariable UUID operation) {
        gates.acquire(account, operation);
        return ApiResponse.onSuccess(state.inspect(account), SuccessCode.OK);
    }

    @DeleteMapping("/fences/{operation}")
    public ApiResponse<Void> release(@PathVariable UUID account, @PathVariable UUID operation) {
        gates.release(account, operation);
        return ApiResponse.onSuccess(null, SuccessCode.OK);
    }

    @GetMapping("/payments")
    public ApiResponse<Payments> payments(
            @PathVariable UUID account, @RequestParam(defaultValue = "0") int page) {
        return ApiResponse.onSuccess(state.payments(account, page), SuccessCode.OK);
    }
}
