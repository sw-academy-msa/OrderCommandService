package com.msa.order.domain.order.saga.service.impl;

import static com.msa.order.domain.order.entity.PurchaseOrder.State.*;

import com.msa.order.domain.order.client.*;
import com.msa.order.domain.order.client.dto.response.*;
import com.msa.order.domain.order.saga.dto.response.*;
import com.msa.order.domain.order.saga.service.*;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.*;

/** Order가 Saga를 조율합니다. Product가 결제 API나 다른 서비스 DB를 호출하게 하지 않습니다. */
@Service
public class OrderSagaServiceImpl implements OrderSagaService {
    private final SagaStore store;
    private final ProductClient product;
    private final PaymentClient payment;
    private final Clock clock;

    public OrderSagaServiceImpl(
            SagaStore store, ProductClient product, PaymentClient payment, Clock clock) {
        this.store = store;
        this.product = product;
        this.payment = payment;
        this.clock = clock;
    }

    public void recover() {
        for (int i = 0; i < 20; i++) {
            UUID id = store.nextDue();
            if (id == null) {
                return;
            }
            advance(id);
        }
    }

    public void advance(UUID id) {
        // 각 단계만 먼저 커밋합니다. 프로세스 중단은 30초 임대 만료 후 동일 키로 복구합니다.
        for (int step = 0; step < 8; step++) {
            SagaTask task = store.claim(id);
            if (task == null) {
                return;
            }
            try {
                store.complete(task, execute(task));
            } catch (RemoteFailure failure) {
                if (task.state() == RESERVING && failure.definitive()) {
                    store.complete(task, new SagaOutcome(FAILED, null, null, failure.code()));
                } else if (task.state() == STARTING_PAYMENT
                        && failure.definitive()
                        && "PRODUCT_EXPIRED".equals(failure.code())) {
                    store.complete(
                            task, new SagaOutcome(RELEASING_STOCK, null, null, failure.code()));
                } else {
                    store.retry(task, failure.code());
                }
                return;
            } catch (RuntimeException failure) {
                store.retry(task, "RECOVERY_REQUIRED");
                return;
            }
        }
    }

    private SagaOutcome execute(SagaTask t) {
        return switch (t.state()) {
            case RESERVING -> {
                var r = product.reserve(t.orderId(), t.productId(), t.buyerId(), t.items());
                validate(t, r);
                if (Set.of("EXPIRED", "RELEASED").contains(r.status())) {
                    yield new SagaOutcome(EXPIRED, null, null, "PAYMENT_DEADLINE_EXCEEDED");
                }
                if (!"HELD".equals(r.status())) {
                    throw new RemoteFailure("RESERVATION_STATE_UNKNOWN", false);
                }
                yield new SagaOutcome(HELD, r, null, null);
            }
            case HELD -> expire(t);
            case STARTING_PAYMENT -> {
                var r = product.begin(t.reservationId(), t.paymentAttemptId());
                validate(t, r);
                if (!"PAYMENT_PROCESSING".equals(r.status())) {
                    throw new RemoteFailure("RESERVATION_STATE_UNKNOWN", false);
                }
                yield new SagaOutcome(APPROVING_PAYMENT, null, null, null);
            }
            case APPROVING_PAYMENT ->
                    evaluate(
                            t,
                            payment.approve(
                                    t.orderId(),
                                    t.paymentAttemptId(),
                                    t.totalAmount(),
                                    t.paymentKey()));
            case VERIFYING_PAYMENT -> evaluate(t, payment.get(t.paymentAttemptId()));
            case CONFIRMING_STOCK -> {
                var r = product.confirm(t.reservationId(), t.paymentAttemptId(), t.approvedAt());
                validate(t, r);
                if (!"CONFIRMED".equals(r.status())) {
                    throw new RemoteFailure("STOCK_CONFIRMATION_UNKNOWN", false);
                }
                yield new SagaOutcome(CONFIRMED, null, null, null);
            }
            case COMPENSATING_PAYMENT -> {
                var r = payment.compensate(t.orderId(), t.paymentAttemptId());
                validate(t, r);
                if (r.status() != PaymentReply.Status.CANCELED) {
                    throw new RemoteFailure("COMPENSATION_PENDING", false);
                }
                yield new SagaOutcome(RELEASING_STOCK, null, null, "LATE_PAYMENT_COMPENSATED");
            }
            case RELEASING_STOCK -> {
                var r = product.release(t.reservationId(), t.paymentAttemptId(), true);
                validate(t, r);
                if (!Set.of("RELEASED", "EXPIRED").contains(r.status())) {
                    throw new RemoteFailure("RELEASE_RESULT_UNKNOWN", false);
                }
                yield new SagaOutcome(
                        clock.instant().isBefore(t.expiresAt()) ? FAILED : EXPIRED,
                        null,
                        null,
                        "PAYMENT_NOT_COMPLETED");
            }
            default -> throw new IllegalStateException("종료 주문은 Saga 작업으로 실행할 수 없습니다.");
        };
    }

    private SagaOutcome expire(SagaTask t) {
        var r = product.get(t.reservationId());
        validate(t, r);
        if ("HELD".equals(r.status()) && !clock.instant().isBefore(t.expiresAt())) {
            r = product.release(t.reservationId(), null, false);
            validate(t, r);
        }
        if (!Set.of("RELEASED", "EXPIRED").contains(r.status())) {
            throw new RemoteFailure("EXPIRY_RESULT_UNKNOWN", false);
        }
        return new SagaOutcome(EXPIRED, null, null, "PAYMENT_DEADLINE_EXCEEDED");
    }

    private SagaOutcome evaluate(SagaTask t, PaymentReply r) {
        validate(t, r);
        if (r.status() == PaymentReply.Status.APPROVED) {
            if (r.approvedAt() == null) {
                throw new RemoteFailure("INVALID_PAYMENT_RESPONSE", false);
            }
            return new SagaOutcome(
                    r.approvedAt().isBefore(t.expiresAt())
                            ? CONFIRMING_STOCK
                            : COMPENSATING_PAYMENT,
                    null,
                    r,
                    null);
        }
        if (r.status() == PaymentReply.Status.DECLINED
                || r.status() == PaymentReply.Status.CANCELED) {
            return new SagaOutcome(RELEASING_STOCK, null, null, "PAYMENT_DECLINED");
        }
        throw new RemoteFailure("PAYMENT_RESULT_PENDING", false);
    }

    private void validate(SagaTask t, PaymentReply r) {
        if (r == null
                || !t.orderId().equals(r.orderId())
                || !t.paymentAttemptId().equals(r.paymentAttemptId())
                || !Objects.equals(t.totalAmount(), r.amount())
                || r.status() == null) {
            throw new RemoteFailure("INVALID_PAYMENT_RESPONSE", false);
        }
    }

    private void validate(SagaTask t, StockReply r) {
        if (r == null
                || r.id() == null
                || !t.orderId().equals(r.orderId())
                || !t.productId().equals(r.productId())
                || !t.buyerId().equals(r.buyerId())
                || !t.items().equals(r.items())
                || r.expiresAt() == null
                || t.reservationId() != null && !t.reservationId().equals(r.id())
                || t.expiresAt() != null && !t.expiresAt().equals(r.expiresAt())
                || r.sellerId() == null
                || r.productName() == null
                || r.productName().isBlank()
                || r.productName().length() > 200
                || r.unitPrice() <= 0
                || r.shippingFee() < 0
                || r.dispatchBusinessDays() == null
                || r.dispatchBusinessDays() <= 0
                || r.optionLabels() == null
                || !r.optionLabels().keySet().equals(t.items().keySet())
                || r.optionLabels().values().stream()
                        .anyMatch(v -> v == null || v.isBlank() || v.length() > 500)) {
            throw new RemoteFailure("INVALID_STOCK_RESPONSE", false);
        }
    }
}
