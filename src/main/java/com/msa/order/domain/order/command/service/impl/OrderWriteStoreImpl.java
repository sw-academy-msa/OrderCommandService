package com.msa.order.domain.order.command.service.impl;

import static com.msa.order.domain.order.entity.PurchaseOrder.State.*;

import com.msa.order.domain.order.OrderError;
import com.msa.order.domain.order.command.dto.request.OrderRequests;
import com.msa.order.domain.order.command.service.*;
import com.msa.order.domain.order.entity.*;
import com.msa.order.domain.order.repository.OrderRepository;
import com.msa.order.global.error.BusinessException;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.*;

/** 외부 호출 없는 짧은 DB 트랜잭션입니다. 같은 요청 키는 advisory lock과 유일 제약으로 보호합니다. */
@Service
@Transactional(timeout = 5)
public class OrderWriteStoreImpl implements OrderWriteStore {
    private final com.msa.order.global.config.checkout.CheckoutProperties checkout;
    private final OrderRepository orders;
    private final OrderJournal journal;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public OrderWriteStoreImpl(
            OrderRepository orders,
            OrderJournal journal,
            JdbcTemplate jdbc,
            Clock clock,
            com.msa.order.global.config.checkout.CheckoutProperties checkout) {
        this.orders = orders;
        this.journal = journal;
        this.jdbc = jdbc;
        this.clock = clock;
        this.checkout = checkout;
    }

    public UUID existing(UUID buyer, UUID key, String hash) {
        var o = orders.findByBuyerIdAndRequestKey(buyer, key);
        if (o.isEmpty()) {
            return null;
        }
        if (!o.get().requestHash.equals(hash)) {
            fail(OrderError.DUPLICATE);
        }
        return o.get().id;
    }

    public UUID create(UUID buyer, OrderRequests.Create r, String hash, ShippingAddress address) {
        jdbc.queryForObject(
                "select pg_advisory_xact_lock(hashtextextended(?, 717))",
                Object.class,
                buyer + ":" + r.requestKey());
        UUID old = existing(buyer, r.requestKey(), hash);
        if (old != null) {
            return old;
        }
        var o = new PurchaseOrder(buyer, r.requestKey(), hash, r.productId(), clock.instant());
        r.items().stream()
                .sorted(Comparator.comparing(OrderRequests.Item::optionId))
                .forEach(i -> o.items.add(new OrderItem(i.optionId(), i.quantity())));
        o.address = address;
        orders.saveAndFlush(o);
        journal.changed(o, "ORDER_CREATED", buyer);
        return o.id;
    }

    public void address(UUID buyer, UUID id, OrderRequests.Address r, ShippingAddress address) {
        var o = owned(id, buyer, false);
        if (o.state != HELD
                || !clock.instant().isBefore(o.expiresAt)
                || o.version != r.expectedVersion()) {
            fail(OrderError.STATE);
        }
        o.address = address;
        journal.changed(o, "ADDRESS_CHANGED", buyer);
    }

    public void payment(UUID buyer, UUID id, OrderRequests.Payment r) {
        var o = owned(id, buyer, false);
        if (o.paymentAttemptId != null) {
            if (!o.paymentAttemptId.equals(r.requestKey())
                    || !Objects.equals(o.termsVersion, r.termsVersion())
                    || !Objects.equals(o.paymentKeyHash, paymentHash(r.paymentKey()))) {
                fail(OrderError.DUPLICATE);
            }
            return;
        }
        if (!checkout.termsVersion().equals(r.termsVersion())) {
            fail(OrderError.INVALID);
        }
        if (o.state != HELD
                || o.expiresAt == null
                || !clock.instant().isBefore(o.expiresAt)
                || !r.agreed()
                || o.totalAmount == null) {
            fail(OrderError.STATE);
        }
        o.paymentAttemptId = r.requestKey();
        o.paymentKey = r.paymentKey();
        o.paymentKeyHash = paymentHash(r.paymentKey());
        o.termsVersion = r.termsVersion();
        o.consentedAt = clock.instant();
        o.state = STARTING_PAYMENT;
        o.nextAttemptAt = clock.instant();
        journal.changed(o, "PAYMENT_REQUESTED", buyer);
    }

    public void shipment(UUID seller, UUID id, OrderRequests.Shipment r) {
        var o = owned(id, seller, true);
        if (o.delivery == PurchaseOrder.Delivery.SHIPPING
                && Objects.equals(o.carrier, r.carrier().trim())
                && Objects.equals(o.trackingNumber, r.trackingNumber())) {
            return;
        }
        if (o.state != CONFIRMED
                || o.delivery != PurchaseOrder.Delivery.PREPARING
                || o.version != r.expectedVersion()) {
            fail(OrderError.STATE);
        }
        o.carrier = r.carrier().trim();
        o.trackingNumber = r.trackingNumber();
        o.delivery = PurchaseOrder.Delivery.SHIPPING;
        o.shippedAt = clock.instant();
        journal.changed(o, "SHIPMENT_REGISTERED", seller);
    }

    public void deliver(UUID seller, UUID id, OrderRequests.Delivery r) {
        var o = owned(id, seller, true);
        if (o.delivery == PurchaseOrder.Delivery.DELIVERED) {
            return;
        }
        if (o.state != CONFIRMED
                || o.delivery != PurchaseOrder.Delivery.SHIPPING
                || o.version != r.expectedVersion()) {
            fail(OrderError.STATE);
        }
        o.delivery = PurchaseOrder.Delivery.DELIVERED;
        o.deliveredAt = clock.instant();
        journal.changed(o, "DELIVERY_MANUALLY_COMPLETED", seller);
    }

    private PurchaseOrder owned(UUID id, UUID actor, boolean seller) {
        var o = orders.lock(id).orElseThrow(() -> new BusinessException(OrderError.NOT_FOUND));
        if (!actor.equals(seller ? o.sellerId : o.buyerId)) {
            fail(OrderError.OWNER);
        }
        return o;
    }

    private void fail(OrderError code) {
        throw new BusinessException(code);
    }

    private String paymentHash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            java.security.MessageDigest.getInstance("SHA-256")
                                    .digest(
                                            value.getBytes(
                                                    java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 미지원");
        }
    }
}
