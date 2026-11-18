package com.msa.order.domain.image.command.service;

import com.msa.order.domain.image.command.dto.request.ImageRequest;
import com.msa.order.domain.image.command.dto.response.ImageReply;

import java.util.UUID;

/** 비공개 문의 사진 업로드 검증·소유권·미사용 객체 정리를 담당합니다. */
public interface InquiryImageService {
    ImageReply issue(UUID buyer, UUID order, ImageRequest request);

    ImageReply complete(UUID buyer, UUID order, UUID image);

    ImageReply download(UUID actor, UUID order, UUID image, boolean seller);

    void cleanup(UUID image);

    void discard(UUID buyer, UUID order, UUID image);
}
