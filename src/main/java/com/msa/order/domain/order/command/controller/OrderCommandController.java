package com.msa.order.domain.order.command.controller;

import com.msa.order.domain.order.client.RemoteFailure;
import com.msa.order.domain.order.command.dto.request.OrderRequests;
import com.msa.order.domain.order.command.service.OrderCommandService;
import com.msa.order.domain.order.exception.OrderErrorCode;
import com.msa.order.domain.order.exception.OrderException;
import com.msa.order.domain.order.query.dto.response.CheckoutResponse;
import com.msa.order.domain.order.query.service.OrderStatusQueryService;
import com.msa.order.global.response.ApiResponse;
import com.msa.order.global.response.SuccessCode;

import jakarta.validation.Valid;

import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** 구매자 식별은 JWT sub를 사용합니다. 배송 개인정보 응답은 캐시하지 않습니다. */
@RestController
@RequestMapping("/api/v1/orders")
@PreAuthorize("hasRole('BUYER')")
public class OrderCommandController {
    private final OrderCommandService commands;
    private final OrderStatusQueryService queries;

    public OrderCommandController(OrderCommandService commands, OrderStatusQueryService queries) {
        this.commands = commands;
        this.queries = queries;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseEntity<ApiResponse<CheckoutResponse>> create(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody OrderRequests.Create r) {
        return response(
                () -> commands.create(UUID.fromString(jwt.getSubject()), r, jwt.getTokenValue()),
                true);
    }

    @GetMapping("/{id}/checkout")
    public ResponseEntity<ApiResponse<CheckoutResponse>> checkout(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return response(() -> queries.buyer(UUID.fromString(jwt.getSubject()), id), false);
    }

    @PatchMapping("/{id}/shipping-address")
    public ResponseEntity<ApiResponse<CheckoutResponse>> address(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody OrderRequests.Address r) {
        return response(
                () ->
                        commands.address(
                                UUID.fromString(jwt.getSubject()), id, r, jwt.getTokenValue()),
                false);
    }

    @PostMapping("/{id}/payment-attempts")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseEntity<ApiResponse<CheckoutResponse>> payment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody OrderRequests.Payment r) {
        return response(() -> commands.payment(UUID.fromString(jwt.getSubject()), id, r), true);
    }

    private ResponseEntity<ApiResponse<CheckoutResponse>> response(
            java.util.function.Supplier<CheckoutResponse> task, boolean accepted) {
        try {
            return ResponseEntity.status(accepted ? 202 : 200)
                    .cacheControl(CacheControl.noStore())
                    .body(ApiResponse.onSuccess(task.get(), SuccessCode.OK));
        } catch (RemoteFailure failure) {
            throw new OrderException(OrderErrorCode.UNAVAILABLE);
        }
    }
}
