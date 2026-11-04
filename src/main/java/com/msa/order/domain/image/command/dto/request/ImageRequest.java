package com.msa.order.domain.image.command.dto.request;

import jakarta.validation.constraints.*;

/** MIME 선언은 업로드 완료 시 파일 서명과 다시 비교합니다. */
public record ImageRequest(@NotBlank String contentType, @Positive long sizeBytes) {}
