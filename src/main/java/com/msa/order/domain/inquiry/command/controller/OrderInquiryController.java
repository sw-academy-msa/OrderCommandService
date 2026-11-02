package com.msa.order.domain.inquiry.command.controller;

import com.msa.order.domain.inquiry.command.dto.request.InquiryRequests;
import com.msa.order.domain.inquiry.command.dto.response.InquiryResponse;
import com.msa.order.domain.inquiry.command.service.OrderInquiryService;

import jakarta.validation.Valid;

import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** 주문 문의 작업용 원본 API입니다. 구매자·판매자 경로를 역할과 행 소유권으로 이중 보호합니다. */
@RestController
public class OrderInquiryController {
    private final OrderInquiryService service;

    public OrderInquiryController(OrderInquiryService service) {
        this.service = service;
    }

    @PostMapping("/api/v1/orders/{order}/inquiries")
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<InquiryResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID order,
            @Valid @RequestBody InquiryRequests.Create r) {
        return ok(service.create(UUID.fromString(jwt.getSubject()), order, r));
    }

    @GetMapping("/api/v1/orders/{order}/inquiries/{id}/editing")
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<InquiryResponse> buyer(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID order, @PathVariable UUID id) {
        return ok(service.get(UUID.fromString(jwt.getSubject()), order, id, false));
    }

    @PostMapping("/api/v1/orders/{order}/inquiries/{id}/messages")
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<InquiryResponse> followup(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID order,
            @PathVariable UUID id,
            @Valid @RequestBody InquiryRequests.Message r) {
        return ok(service.message(UUID.fromString(jwt.getSubject()), order, id, false, r));
    }

    @GetMapping("/api/v1/seller/orders/{order}/inquiries/{id}/editing")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<InquiryResponse> seller(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID order, @PathVariable UUID id) {
        return ok(service.get(UUID.fromString(jwt.getSubject()), order, id, true));
    }

    @PostMapping("/api/v1/seller/orders/{order}/inquiries/{id}/answers")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<InquiryResponse> answer(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID order,
            @PathVariable UUID id,
            @Valid @RequestBody InquiryRequests.Message r) {
        return ok(service.message(UUID.fromString(jwt.getSubject()), order, id, true, r));
    }

    private ResponseEntity<InquiryResponse> ok(InquiryResponse body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
