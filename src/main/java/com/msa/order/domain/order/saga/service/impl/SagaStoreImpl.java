package com.msa.order.domain.order.saga.service.impl;

import static com.msa.order.domain.order.entity.PurchaseOrder.State.*;

import com.msa.order.domain.order.command.service.OrderJournal;
import com.msa.order.domain.order.entity.*;
import com.msa.order.domain.order.repository.OrderRepository;
import com.msa.order.domain.order.saga.dto.response.*;
import com.msa.order.domain.order.saga.service.SagaStore;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/** 복제본/재시작 경합은 DB 임대 토큰으로 보호하고 오래된 HTTP 결과는 버립니다. */
@Service
@Transactional(timeout = 5)
public class SagaStoreImpl implements SagaStore {
    private final OrderRepository orders;
    private final JdbcTemplate jdbc;
    private final OrderJournal journal;
    private final Clock clock;

    public SagaStoreImpl(
            OrderRepository orders, JdbcTemplate jdbc, OrderJournal journal, Clock clock) {
        this.orders = orders;
        this.jdbc = jdbc;
        this.journal = journal;
        this.clock = clock;
    }

    public UUID nextDue() {
        var rows =
                jdbc.queryForList(
                        "select id from orders.purchase_orders where next_attempt_at <= ? and"
                            + " (lease_until is null or lease_until <= ?) order by next_attempt_at,"
                            + " id limit 1",
                        UUID.class,
                        Timestamp.from(clock.instant()),
                        Timestamp.from(clock.instant()));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public SagaTask claim(UUID id) {
        var found = orders.lock(id);
        if (found.isEmpty()) {
            return null;
        }
        var o = found.get();
        Instant now = clock.instant();
        if (o.terminal()
                || o.nextAttemptAt == null
                || o.nextAttemptAt.isAfter(now)
                || o.leaseUntil != null && o.leaseUntil.isAfter(now)) {
            return null;
        }
        o.leaseToken = UUID.randomUUID();
        o.leaseUntil = now.plusSeconds(30);
        o.attempts++;
        return new SagaTask(
                o.id,
                o.leaseToken,
                o.state,
                o.productId,
                o.buyerId,
                o.items.stream().collect(Collectors.toMap(i -> i.optionId, i -> i.quantity)),
                o.reservationId,
                o.expiresAt,
                o.paymentAttemptId,
                o.totalAmount,
                o.paymentKey,
                o.paymentApprovedAt);
    }

    public void complete(SagaTask task, SagaOutcome outcome) {
        var o = orders.lock(task.orderId()).orElseThrow();
        if (!Objects.equals(o.leaseToken, task.leaseToken()) || o.state != task.state()) {
            return;
        }
        var r = outcome.stock();
        if (r != null && o.reservationId == null) {
            o.reservationId = r.id();
            o.sellerId = r.sellerId();
            o.productName = r.productName();
            o.brandOrStore = r.brandOrStore();
            o.dispatchBusinessDays = r.dispatchBusinessDays();
            o.unitPrice = r.unitPrice();
            o.shippingFee = r.shippingFee();
            o.expiresAt = r.expiresAt();
            long count = o.items.stream().mapToLong(i -> i.quantity).sum();
            try {
                o.totalAmount =
                        Math.addExact(Math.multiplyExact(o.unitPrice, count), o.shippingFee);
            } catch (ArithmeticException overflow) {
                // 유효한 원본이라도 합계가 표현 범위를 넘으면 결제를 시작하지 않고 확보를 해제합니다.
                o.totalAmount = null;
            }
            o.items.forEach(i -> i.label = r.optionLabels().get(i.optionId));
        }
        if (outcome.payment() != null && outcome.payment().approvedAt() != null) {
            o.paymentApprovedAt = outcome.payment().approvedAt();
        }
        o.state = outcome.state();
        o.lastFailureCode = outcome.failure();
        if (o.reservationId != null && o.totalAmount == null) {
            o.state = RELEASING_STOCK;
            o.lastFailureCode = "AMOUNT_OVERFLOW";
        }
        if (o.state == CONFIRMED) {
            o.confirmedAt = clock.instant();
            o.delivery = PurchaseOrder.Delivery.PREPARING;
        }
        if (o.terminal()) {
            o.paymentKey = null;
        }
        o.leaseToken = null;
        o.leaseUntil = null;
        o.nextAttemptAt = o.terminal() ? null : o.state == HELD ? o.expiresAt : clock.instant();
        journal.changed(o, "SAGA_" + o.state, null);
    }

    public void retry(SagaTask task, String failure) {
        var o = orders.lock(task.orderId()).orElseThrow();
        if (!Objects.equals(o.leaseToken, task.leaseToken()) || o.state != task.state()) {
            return;
        }
        // 결과 미도착 시 승인 명령을 중복 생성하지 않고 로컬 결과와 Kafka CHECK로 복구합니다.
        if (o.state == APPROVING_PAYMENT) {
            o.state = VERIFYING_PAYMENT;
        }
        o.lastFailureCode = failure;
        o.leaseToken = null;
        o.leaseUntil = null;
        o.nextAttemptAt = clock.instant().plusSeconds(Math.min(60, 1L << Math.min(6, o.attempts)));
        journal.changed(o, "RETRY_" + failure, null);
    }
}
