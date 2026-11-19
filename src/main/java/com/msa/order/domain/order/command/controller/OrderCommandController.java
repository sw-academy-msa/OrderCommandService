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
import org.springframework.security.core.Authentication;
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
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @Valid @RequestBody OrderRequests.Create r) {
        // 운영자도 BUYER 역할을 함께 가지므로 역할 검사만으로는 막히지 않습니다. 주문 생성 전에 거절합니다.
        if (isAdmin(authentication)) {
            throw new OrderException(OrderErrorCode.FORBIDDEN);
        }
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

    /** 구매 확정(배송 완료 주문). 이미 확정했으면 현재 상태를 그대로 돌려줍니다 */
    @PostMapping("/{id}/purchase-confirmation")
    public ResponseEntity<ApiResponse<CheckoutResponse>> confirmPurchase(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return response(
                () -> commands.confirmPurchase(UUID.fromString(jwt.getSubject()), id), false);
    }

    /** 보안 설정의 roles 변환기가 부여한 권한으로 판단합니다. 원본 클레임을 따로 해석하지 않아 형식이 달라도 판단이 어긋나지 않습니다. */
    private boolean isAdmin(Authentication authentication) {
        return authentication != null
                && authentication.getAuthorities().stream()
                        .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
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
