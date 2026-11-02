package com.msa.order.domain.order.command.controller;

import com.msa.order.domain.order.command.dto.request.OrderRequests;
import com.msa.order.domain.order.command.service.OrderCommandService;
import com.msa.order.domain.order.query.dto.response.CheckoutResponse;
import com.msa.order.domain.order.query.service.OrderStatusQueryService;

import jakarta.validation.Valid;

import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** SELLER 역할 외에도 서비스에서 해당 주문의 판매자인지 재검증합니다. */
@RestController
@RequestMapping("/api/v1/seller/orders")
@PreAuthorize("hasRole('SELLER')")
public class SellerOrderCommandController {
    private final OrderCommandService commands;
    private final OrderStatusQueryService queries;

    public SellerOrderCommandController(
            OrderCommandService commands, OrderStatusQueryService queries) {
        this.commands = commands;
        this.queries = queries;
    }

    @GetMapping("/{id}/fulfillment")
    public ResponseEntity<CheckoutResponse> edit(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ok(queries.seller(UUID.fromString(jwt.getSubject()), id));
    }

    @PostMapping("/{id}/shipment")
    public ResponseEntity<CheckoutResponse> ship(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody OrderRequests.Shipment r) {
        return ok(commands.shipment(UUID.fromString(jwt.getSubject()), id, r));
    }

    @PostMapping("/{id}/delivery-completion")
    public ResponseEntity<CheckoutResponse> deliver(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody OrderRequests.Delivery r) {
        return ok(commands.deliver(UUID.fromString(jwt.getSubject()), id, r));
    }

    private ResponseEntity<CheckoutResponse> ok(CheckoutResponse body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
