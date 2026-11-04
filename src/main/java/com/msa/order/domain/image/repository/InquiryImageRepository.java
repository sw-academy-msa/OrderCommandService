package com.msa.order.domain.image.repository;

import com.msa.order.domain.image.entity.InquiryImage;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

import java.time.Instant;
import java.util.*;

/** 첨부 연결과 업로드 완료/정리 경합은 이미지 행 잠금으로 제어합니다. */
public interface InquiryImageRepository extends JpaRepository<InquiryImage, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InquiryImage i where i.id = :id")
    Optional<InquiryImage> lock(UUID id);

    List<InquiryImage> findByInquiryId(UUID inquiryId);

    @Query(
            "select count(i) from InquiryImage i where i.orderId = :orderId and i.inquiryId is null"
                    + " and i.cleaned = false and i.discarded = false and i.expiresAt > :now")
    long countPending(UUID orderId, Instant now);

    @Query(
            "select i.id from InquiryImage i where i.cleaned = false and i.expiresAt < :cutoff"
                    + " order by i.expiresAt")
    List<UUID> cleanupIds(Instant cutoff, Pageable page);
}
