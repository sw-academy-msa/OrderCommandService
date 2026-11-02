package com.msa.order.domain.order.repository;

import com.msa.order.domain.order.entity.PurchaseOrder;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.*;

import java.util.*;

/** 주문별 단기 쓰기 잠금으로 API·Saga 상태 전이를 직렬화합니다. */
public interface OrderRepository extends JpaRepository<PurchaseOrder, UUID> {
    Optional<PurchaseOrder> findByBuyerIdAndRequestKey(UUID buyerId, UUID requestKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PurchaseOrder o where o.id = :id")
    Optional<PurchaseOrder> lock(UUID id);
}
