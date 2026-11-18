package com.msa.order.domain.withdrawal.repository;

import com.msa.order.domain.order.entity.PurchaseOrder;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

/** 자기 orders 원본만 조회합니다. 배송 완료 전 확정 주문도 진행 거래입니다. */
public interface WithdrawalStateRepository extends JpaRepository<PurchaseOrder, UUID> {
    @Query(
            "select count(o) from PurchaseOrder o where (o.buyerId=:account or o.sellerId=:account)"
                + " and o.state not in ('FAILED','EXPIRED') and (o.state<>'CONFIRMED' or o.delivery"
                + " is null or o.delivery<>'DELIVERED')")
    long activeOrders(@Param("account") UUID account);

    @Query(
            "select o from PurchaseOrder o where (o.buyerId=:account or o.sellerId=:account) and"
                    + " o.paymentAttemptId is not null order by o.id")
    Slice<PurchaseOrder> payments(@Param("account") UUID account, Pageable page);
}
