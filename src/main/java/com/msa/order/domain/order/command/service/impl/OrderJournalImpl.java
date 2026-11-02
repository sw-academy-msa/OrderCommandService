package com.msa.order.domain.order.command.service.impl;

import com.msa.order.domain.order.command.service.OrderJournal;
import com.msa.order.domain.order.entity.*;
import com.msa.order.domain.outbox.entity.OutboxEvent;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.*;

/** Query 계약 초안에 주문 스냅샷을 남깁니다. 결제 키·내부 임대·토큰은 발행하지 않습니다. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class OrderJournalImpl implements OrderJournal {
    private final EntityManager em;
    private final JsonMapper json;
    private final Clock clock;

    public OrderJournalImpl(EntityManager em, JsonMapper json, Clock clock) {
        this.em = em;
        this.json = json;
        this.clock = clock;
    }

    public void changed(PurchaseOrder o, String action, UUID actor) {
        o.updatedAt = clock.instant();
        o.projectionVersion++;
        em.persist(new OrderHistory(o.id, action, actor, clock.instant()));
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("orderId", o.id);
        snapshot.put("buyerId", o.buyerId);
        snapshot.put("sellerId", o.sellerId);
        snapshot.put("productId", o.productId);
        snapshot.put("productName", o.productName);
        snapshot.put("brandOrStore", o.brandOrStore);
        snapshot.put("items", o.items);
        snapshot.put("state", o.state);
        snapshot.put("delivery", o.delivery);
        snapshot.put("address", o.address);
        snapshot.put("unitPrice", o.unitPrice);
        snapshot.put("shippingFee", o.shippingFee);
        snapshot.put("totalAmount", o.totalAmount);
        snapshot.put("dispatchBusinessDays", o.dispatchBusinessDays);
        snapshot.put("expiresAt", o.expiresAt);
        snapshot.put("createdAt", o.createdAt);
        snapshot.put("confirmedAt", o.confirmedAt);
        snapshot.put("carrier", o.carrier);
        snapshot.put("trackingNumber", o.trackingNumber);
        snapshot.put("shippedAt", o.shippedAt);
        snapshot.put("deliveredAt", o.deliveredAt);
        UUID eventId = UUID.randomUUID();
        var event =
                Map.of(
                        "eventId",
                        eventId,
                        "schemaVersion",
                        1,
                        "type",
                        "ORDER_UPSERT",
                        "aggregateId",
                        o.id,
                        "aggregateVersion",
                        o.projectionVersion,
                        "occurredAt",
                        clock.instant(),
                        "order",
                        snapshot);
        em.persist(
                new OutboxEvent(
                        eventId,
                        "stockdrop.orders.private.v1",
                        o.id,
                        json.writeValueAsString(event),
                        clock.instant()));
    }
}
