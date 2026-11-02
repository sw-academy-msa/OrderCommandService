package com.msa.order.domain.image.command.controller;

import com.msa.order.domain.image.command.dto.request.ImageRequest;
import com.msa.order.domain.image.command.dto.response.ImageReply;
import com.msa.order.domain.image.command.service.InquiryImageService;

import jakarta.validation.Valid;

import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** 브라우저는 서버가 발급한 제한된 S3 업로드 권한만 사용합니다. */
@RestController
public class InquiryImageController {
    private final InquiryImageService images;

    public InquiryImageController(InquiryImageService images) {
        this.images = images;
    }

    @PostMapping("/api/v1/orders/{order}/inquiry-images")
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<ImageReply> issue(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID order,
            @Valid @RequestBody ImageRequest r) {
        return ok(images.issue(UUID.fromString(jwt.getSubject()), order, r));
    }

    @PostMapping("/api/v1/orders/{order}/inquiry-images/{id}/completion")
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<ImageReply> complete(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID order, @PathVariable UUID id) {
        return ok(images.complete(UUID.fromString(jwt.getSubject()), order, id));
    }

    @GetMapping("/api/v1/orders/{order}/inquiry-images/{id}/download")
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<ImageReply> buyer(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID order, @PathVariable UUID id) {
        return ok(images.download(UUID.fromString(jwt.getSubject()), order, id, false));
    }

    @GetMapping("/api/v1/seller/orders/{order}/inquiry-images/{id}/download")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ImageReply> seller(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID order, @PathVariable UUID id) {
        return ok(images.download(UUID.fromString(jwt.getSubject()), order, id, true));
    }

    @DeleteMapping("/api/v1/orders/{order}/inquiry-images/{id}")
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<Void> discard(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID order, @PathVariable UUID id) {
        images.discard(UUID.fromString(jwt.getSubject()), order, id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private ResponseEntity<ImageReply> ok(ImageReply r) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(r);
    }
}
