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
    private final com.msa.order.domain.notification.service.BusinessNoticeService notices;
    private final EntityManager em;
    private final JsonMapper json;
    private final Clock clock;

    public OrderJournalImpl(
            com.msa.order.domain.notification.service.BusinessNoticeService notices,
            EntityManager em,
            JsonMapper json,
            Clock clock) {
        this.notices = notices;
        this.em = em;
        this.json = json;
        this.clock = clock;
    }

    public void changed(PurchaseOrder o, String action, UUID actor) {
        o.updatedAt = clock.instant();
        o.projectionVersion++;
        em.persist(
                OrderHistory.builder()
                        .orderId(o.id)
                        .action(action)
                        .actor(actor)
                        .now(clock.instant())
                        .build());
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
                OutboxEvent.builder()
                        .id(eventId)
                        .topic("stockdrop.orders.private.v1")
                        .aggregateId(o.id)
                        .payload(json.writeValueAsString(event))
                        .now(clock.instant())
                        .build());
        // 알림은 원본 상태 전이가 확정되는 지점에서만 기록합니다. Saga 중간 단계와 재시도는 제외합니다.
        if ("SAGA_CONFIRMED".equals(action)) {
            notices.enqueue(
                    o.buyerId,
                    "ORDER_CONFIRMED",
                    o.id,
                    o.projectionVersion,
                    "ORDER",
                    o.id,
                    null,
                    null);
            // 과거 확보에 판매자 스냅샷이 없으면 추정하지 않습니다. 본인 구매는 같은 계정에 한 번만 기록합니다.
            if (o.sellerId != null && !o.sellerId.equals(o.buyerId))
                notices.enqueue(
                        o.sellerId,
                        "ORDER_CONFIRMED",
                        o.id,
                        o.projectionVersion,
                        "SELLER_ORDER",
                        o.id,
                        null,
                        null);
        } else if ("SHIPMENT_REGISTERED".equals(action)) {
            notices.enqueue(
                    o.buyerId,
                    "ORDER_SHIPPED",
                    o.id,
                    o.projectionVersion,
                    "ORDER",
                    o.id,
                    null,
                    null);
        } else if ("DELIVERY_MANUALLY_COMPLETED".equals(action)) {
            notices.enqueue(
                    o.buyerId,
                    "ORDER_DELIVERED",
                    o.id,
                    o.projectionVersion,
                    "ORDER",
                    o.id,
                    null,
                    null);
        }
    }
}
