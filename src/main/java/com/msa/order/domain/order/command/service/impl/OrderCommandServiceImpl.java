package com.msa.order.domain.order.command.service.impl;

import com.msa.order.domain.order.client.AddressClient;
import com.msa.order.domain.order.command.dto.request.OrderRequests;
import com.msa.order.domain.order.command.service.*;
import com.msa.order.domain.order.exception.OrderErrorCode;
import com.msa.order.domain.order.exception.OrderException;
import com.msa.order.domain.order.query.dto.response.CheckoutResponse;
import com.msa.order.domain.order.query.service.OrderStatusQueryService;
import com.msa.order.domain.order.saga.service.OrderSagaService;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** HTTP/DB/HTTP를 명시적으로 분리합니다. 응답 실패가 나도 영속 주문과 Saga를 삭제하지 않습니다. */
@Service
public class OrderCommandServiceImpl implements OrderCommandService {
    private final OrderWriteStore store;
    private final OrderStatusQueryService queries;
    private final OrderSagaService saga;
    private final AddressClient addresses;

    public OrderCommandServiceImpl(
            OrderWriteStore store,
            OrderStatusQueryService queries,
            OrderSagaService saga,
            AddressClient addresses) {
        this.store = store;
        this.queries = queries;
        this.saga = saga;
        this.addresses = addresses;
    }

    public CheckoutResponse create(UUID buyer, OrderRequests.Create r, String token) {
        if (r.items() == null
                || r.items().isEmpty()
                || r.items().stream()
                        .anyMatch(i -> i == null || i.optionId() == null || i.quantity() <= 0)
                || r.items().stream().map(OrderRequests.Item::optionId).distinct().count()
                        != r.items().size()
                || r.items().stream().mapToLong(OrderRequests.Item::quantity).sum()
                        > Integer.MAX_VALUE) {
            throw new OrderException(OrderErrorCode.INVALID);
        }
        String hash = hash(r);
        UUID id = store.existing(buyer, r.requestKey(), hash);
        if (id == null) {
            id = store.create(buyer, r, hash, addresses.get(r.addressId(), token));
        }
        saga.advance(id);
        return queries.buyer(buyer, id);
    }

    public CheckoutResponse address(UUID buyer, UUID id, OrderRequests.Address r, String token) {
        queries.buyer(buyer, id);
        store.address(buyer, id, r, addresses.get(r.addressId(), token));
        return queries.buyer(buyer, id);
    }

    public CheckoutResponse payment(UUID buyer, UUID id, OrderRequests.Payment r) {
        store.payment(buyer, id, r);
        saga.advance(id);
        return queries.buyer(buyer, id);
    }

    public CheckoutResponse shipment(UUID seller, UUID id, OrderRequests.Shipment r) {
        store.shipment(seller, id, r);
        return queries.seller(seller, id);
    }

    public CheckoutResponse deliver(UUID seller, UUID id, OrderRequests.Delivery r) {
        store.deliver(seller, id, r);
        return queries.seller(seller, id);
    }

    private String hash(OrderRequests.Create r) {
        String value =
                r.productId()
                        + ":"
                        + r.addressId()
                        + ":"
                        + r.items().stream()
                                .sorted(Comparator.comparing(OrderRequests.Item::optionId))
                                .map(i -> i.optionId() + "=" + i.quantity())
                                .toList();
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.");
        }
    }
}
