package com.msa.order.domain.withdrawal.repository;

import com.msa.order.domain.withdrawal.entity.WithdrawalGate;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.*;

import java.util.UUID;

/** 아직 차단 행이 없는 최초 거래도 PostgreSQL 트랜잭션 advisory lock으로 직렬화합니다. */
public interface WithdrawalGateRepository extends JpaRepository<WithdrawalGate, UUID> {
    @Transactional(propagation = Propagation.MANDATORY)
    @Query(
            value = "select true from pg_advisory_xact_lock(hashtextextended(:identity,1188))",
            nativeQuery = true)
    boolean lockAccount(@Param("identity") String identity);

    // 같은 계정의 일반 거래를 전역 직렬화하지 않고 탈퇴 차단과만 상호 배제합니다.
    @Transactional(propagation = Propagation.MANDATORY)
    @Query(
            value =
                    "select true from"
                        + " pg_advisory_xact_lock_shared(hashtextextended(:identity,1188))",
            nativeQuery = true)
    boolean lockAccountShared(@Param("identity") String identity);
}
