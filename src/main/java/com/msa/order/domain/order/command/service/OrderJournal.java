package com.msa.order.domain.order.command.service;

import com.msa.order.domain.order.entity.PurchaseOrder;

import java.util.UUID;

/** 주문 변경과 이력·Outbox 저장을 같은 트랜잭션으로 묶습니다. */
public interface OrderJournal {
    void changed(PurchaseOrder order, String action, UUID actor);
}
