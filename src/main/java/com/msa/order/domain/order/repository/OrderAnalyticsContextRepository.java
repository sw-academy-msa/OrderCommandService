package com.msa.order.domain.order.repository;

import com.msa.order.domain.order.entity.OrderAnalyticsContext;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

/** 분석 문맥은 30일 뒤 정리하고 일반 주문 조회에는 조인하지 않습니다. */
public interface OrderAnalyticsContextRepository
        extends JpaRepository<OrderAnalyticsContext, UUID> {
    @Modifying
    @Query("delete from OrderAnalyticsContext a where a.createdAt < :cutoff")
    int purge(@Param("cutoff") Instant cutoff);
}
