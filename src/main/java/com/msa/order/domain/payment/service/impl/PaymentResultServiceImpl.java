package com.msa.order.domain.payment.service.impl;

import com.msa.order.domain.order.client.dto.response.PaymentReply.Status;
import com.msa.order.domain.order.repository.OrderRepository;
import com.msa.order.domain.payment.*;
import com.msa.order.domain.payment.dto.response.PaymentResultEvent;
import com.msa.order.domain.payment.entity.*;
import com.msa.order.domain.payment.service.PaymentResultService;

import jakarta.persistence.EntityManager;
import jakarta.validation.Validator;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.security.MessageDigest;
import java.time.Clock;
import java.util.*;

/** 주문 잠금으로 결과/재시도 경합을 직렬화하고 버전·금액·종료 상태를 검증합니다. */
@Service
public class PaymentResultServiceImpl implements PaymentResultService {
    private final EntityManager em;
    private final OrderRepository orders;
    private final JsonMapper json;
    private final Validator validator;
    private final Clock clock;

    public PaymentResultServiceImpl(
            EntityManager em,
            OrderRepository orders,
            JsonMapper json,
            Validator validator,
            Clock clock) {
        this.em = em;
        this.orders = orders;
        this.json = json;
        this.validator = validator;
        this.clock = clock;
    }

    @Transactional(timeout = 5)
    public void apply(String key, byte[] payload) {
        PaymentResultEvent event;
        try {
            Map<?, ?> raw = json.readValue(payload, Map.class);
            if (!(raw.get("status") instanceof String)) {
                invalid();
            }
            for (String field : List.of("schemaVersion", "amount", "version")) {
                Object value = raw.get(field);
                if (!(value instanceof Integer
                        || value instanceof Long
                        || value instanceof java.math.BigInteger)) {
                    invalid();
                }
            }
        } catch (Exception failure) {
            throw new InvalidPaymentEventException();
        }

        try {
            event =
                    json.readerFor(PaymentResultEvent.class)
                            .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                            .without(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                            .readValue(payload);
        } catch (Exception failure) {
            throw new InvalidPaymentEventException();
        }
        if (event == null
                || !validator.validate(event).isEmpty()
                || !event.paymentAttemptId().toString().equals(key)) {
            invalid();
        }
        String hash = hash(json.writeValueAsBytes(event));
        var o = orders.lock(event.orderId()).orElseThrow(InvalidPaymentEventException::new);
        var old = em.find(PaymentResultReceipt.class, event.eventId());
        if (old != null) {
            if (!old.payloadHash.equals(hash)) {
                invalid();
            }
            return;
        }
        var attempt = em.find(PaymentAttempt.class, event.paymentAttemptId());
        if (attempt == null
                || !attempt.orderId.equals(o.id)
                || !event.paymentAttemptId().equals(o.paymentAttemptId)
                || attempt.amount != event.amount()
                || !Objects.equals(o.totalAmount, event.amount())
                || event.approvedAt() != null && event.approvedAt().getNano() % 1000000 != 0
                || event.approvedAt() != null
                        && Set.of(Status.PENDING, Status.UNKNOWN, Status.DECLINED)
                                .contains(event.status())
                || event.status() == Status.APPROVED
                        && (event.approvedAt() == null
                                || event.approvedAt().isBefore(o.createdAt))) {
            invalid();
        }
        var state = new TreeMap<String, Object>();
        state.put("status", event.status());
        state.put("approvedAt", event.approvedAt());
        state.put("amount", event.amount());
        String snapshotHash = hash(json.writeValueAsBytes(state));
        if (event.version() == attempt.resultVersion
                && !Objects.equals(snapshotHash, attempt.resultHash)) {
            invalid();
        }
        if (event.version() > attempt.resultVersion) {
            // terminal은 되돌리지 않습니다. 승인 후 취소는 Order가 보상을 요청한 경우만 허용합니다.
            if ((attempt.status == Status.CANCELED || attempt.status == Status.DECLINED)
                            && event.status() != attempt.status
                    || attempt.status == Status.APPROVED
                            && event.status() != Status.APPROVED
                            && !(event.status() == Status.CANCELED && attempt.compensationRequested)
                    || attempt.approvedAt != null
                            && event.status() == Status.APPROVED
                            && !attempt.approvedAt.equals(event.approvedAt())) {
                invalid();
            }
            attempt.status = event.status();
            attempt.resultVersion = event.version();
            attempt.resultHash = snapshotHash;
            if (event.approvedAt() != null) {
                attempt.approvedAt = event.approvedAt();
            }
            if (!o.terminal()) {
                o.nextAttemptAt = clock.instant();
            }
        }
        em.persist(new PaymentResultReceipt(event.eventId(), hash, clock.instant()));
    }

    private String hash(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 미지원");
        }
    }

    private void invalid() {
        throw new InvalidPaymentEventException();
    }
}
