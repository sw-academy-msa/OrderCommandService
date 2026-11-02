package com.msa.order.domain.order.client.impl;

import com.msa.order.domain.order.client.*;
import com.msa.order.domain.order.client.dto.response.PaymentReply;
import com.msa.order.domain.order.command.service.OrderJournal;
import com.msa.order.domain.order.entity.PurchaseOrder;
import com.msa.order.domain.order.repository.OrderRepository;
import com.msa.order.domain.outbox.entity.OutboxEvent;
import com.msa.order.domain.payment.entity.PaymentAttempt;

import jakarta.persistence.EntityManager;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

import java.time.*;
import java.util.*;

/** Payment HTTP를 사용하지 않습니다. 명령/시도 상태/Outbox를 원자 저장하고 로컬 결과만 반환합니다. */
@Component
@Transactional(timeout = 5)
public class PaymentClientImpl implements PaymentClient {
    private final EntityManager em;
    private final OrderRepository orders;
    private final OrderJournal journal;
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final Clock clock;

    public PaymentClientImpl(
            EntityManager em,
            OrderRepository orders,
            OrderJournal journal,
            JdbcTemplate jdbc,
            JsonMapper json,
            Clock clock) {
        this.em = em;
        this.orders = orders;
        this.journal = journal;
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    public PaymentReply approve(UUID order, UUID attempt, long amount, String paymentKey) {
        var o = locked(order, attempt);
        if (!Objects.equals(o.totalAmount, amount) || !Objects.equals(o.paymentKey, paymentKey)) {
            fail();
        }
        var a = attempt(o);
        if (!a.approvalRequested) {
            a.approvalRequested = true;
            command(o, a, "APPROVE", paymentKey);
            journal.changed(o, "PAYMENT_APPROVAL_ENQUEUED", null);
        }
        return reply(a);
    }

    public PaymentReply get(UUID attempt) {
        var ids =
                jdbc.queryForList(
                        "select id from orders.purchase_orders where payment_attempt_id = ?",
                        UUID.class,
                        attempt);
        if (ids.size() != 1) {
            fail();
        }
        var o = locked(ids.getFirst(), attempt);
        var a = attempt(o);
        if (a.nextCheckAt == null || !a.nextCheckAt.isAfter(clock.instant())) {
            command(o, a, "CHECK", null);
        }
        return reply(a);
    }

    public PaymentReply compensate(UUID order, UUID attempt) {
        var o = locked(order, attempt);
        var a = attempt(o);
        if (!a.compensationRequested) {
            a.compensationRequested = true;
            command(o, a, "CANCEL", null);
            journal.changed(o, "PAYMENT_COMPENSATION_ENQUEUED", null);
        } else if (a.status != PaymentReply.Status.CANCELED
                && !a.nextCheckAt.isAfter(clock.instant())) {
            command(o, a, "CHECK", null);
        }
        return reply(a);
    }

    private PurchaseOrder locked(UUID order, UUID attempt) {
        var o =
                orders.lock(order)
                        .orElseThrow(() -> new RemoteFailure("PAYMENT_ORDER_NOT_FOUND", false));
        if (!attempt.equals(o.paymentAttemptId) || o.totalAmount == null) {
            fail();
        }
        return o;
    }

    private PaymentAttempt attempt(PurchaseOrder o) {
        var a = em.find(PaymentAttempt.class, o.paymentAttemptId);
        if (a == null) {
            a = new PaymentAttempt(o.paymentAttemptId, o.id, o.totalAmount);
            em.persist(a);
        }
        if (!a.orderId.equals(o.id) || a.amount != o.totalAmount) {
            fail();
        }
        return a;
    }

    private void command(PurchaseOrder o, PaymentAttempt a, String action, String paymentKey) {
        UUID id = UUID.randomUUID();
        var event = new LinkedHashMap<String, Object>();
        event.put("eventId", id);
        event.put("schemaVersion", 1);
        event.put("type", "PAYMENT_COMMAND");
        event.put("action", action);
        event.put("orderId", o.id);
        event.put("paymentAttemptId", a.id);
        event.put("commandVersion", ++a.commandVersion);
        event.put("occurredAt", clock.instant());
        event.put("amount", a.amount);
        event.put("expiresAt", o.expiresAt);
        event.put("cancellationRequested", a.compensationRequested);
        // 승인 명령 전용 비공개 토픽에만 포함합니다. Query 이벤트/응답/로그로는 전달하지 않습니다.
        if (paymentKey != null) {
            event.put("paymentKey", paymentKey);
        }
        em.persist(
                new OutboxEvent(
                        id,
                        "stockdrop.payment.commands.private.v1",
                        a.id,
                        json.writeValueAsString(event),
                        clock.instant()));
        a.nextCheckAt = clock.instant().plusSeconds(60);
    }

    private PaymentReply reply(PaymentAttempt a) {
        return new PaymentReply(a.orderId, a.id, a.amount, a.status, a.approvedAt);
    }

    private void fail() {
        throw new RemoteFailure("PAYMENT_COMMAND_CONFLICT", false);
    }
}
