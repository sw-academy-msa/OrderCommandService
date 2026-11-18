package com.msa.order.domain.image.command.scheduling;

import com.msa.order.domain.image.command.service.InquiryImageService;
import com.msa.order.domain.image.repository.InquiryImageRepository;
import com.msa.order.global.config.storage.ImageStorageProperties;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.*;

/** 만료된 업로드의 미사용 객체만 정리하고 S3 장애는 다음 실행에서 재시도합니다. */
@Component
@ConditionalOnProperty(
        name = "order.images.cleanup-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class InquiryImageCleanupJob {
    private final InquiryImageRepository images;
    private final InquiryImageService service;
    private final ImageStorageProperties properties;
    private final Clock clock;

    public InquiryImageCleanupJob(
            InquiryImageRepository images,
            InquiryImageService service,
            ImageStorageProperties properties,
            Clock clock) {
        this.images = images;
        this.service = service;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "${order.images.cleanup-ms:60000}",
            scheduler = "inquiryCleanupScheduler")
    public void cleanup() {
        if (!properties.ready()) {
            return;
        }
        for (var id :
                images.cleanupIds(
                        clock.instant().minus(Duration.ofHours(24)), PageRequest.of(0, 100))) {
            try {
                service.cleanup(id);
            } catch (RuntimeException ex) {
                org.slf4j.LoggerFactory.getLogger(getClass())
                        .warn("문의 이미지 정리 재시도 예정: type={}", ex.getClass().getSimpleName());
            }
        }
    }
}
