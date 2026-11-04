package com.msa.order.domain.order.repository;

import com.msa.order.domain.order.entity.PurchaseOrder;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.*;

import java.time.Instant;
import java.util.*;

/** 주문별 단기 쓰기 잠금으로 API·Saga 상태 전이를 직렬화합니다. */
public interface OrderRepository extends JpaRepository<PurchaseOrder, UUID> {
    Optional<PurchaseOrder> findByBuyerIdAndRequestKey(UUID buyerId, UUID requestKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PurchaseOrder o where o.id = :id")
    Optional<PurchaseOrder> lock(UUID id);

    /** void 반환 PostgreSQL 함수는 FROM 절에서 실행하고 boolean만 매핑합니다. 잠금은 호출자 트랜잭션 종료까지 유지됩니다. */
    @Transactional(propagation = Propagation.MANDATORY)
    @Query(
            value = "select true from pg_advisory_xact_lock(hashtextextended(:identity,717))",
            nativeQuery = true)
    boolean lockRequestCreation(@Param("identity") String identity);

    /** 실제 임대 획득은 lock/상태 검증에서 수행하므로 여기서는 기존과 같이 후보 ID만 조회합니다. */
    @Query(
            value =
                    """
                    select id from orders.purchase_orders
                    where next_attempt_at<=:now and (lease_until is null or lease_until<=:now)
                    order by next_attempt_at,id limit 1
                    """,
            nativeQuery = true)
    Optional<UUID> findNextDueId(@Param("now") Instant now);

    /** 주문 엔티티를 미리 적재하지 않아 이후 쓰기 잠금 전에 오래된 상태가 영속성 컨텍스트에 남지 않습니다. */
    @Query("select o.id from PurchaseOrder o where o.paymentAttemptId=:attempt")
    List<UUID> findIdsByPaymentAttemptId(@Param("attempt") UUID attempt);
}
