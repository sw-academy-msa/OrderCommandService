package com.msa.order.domain.order.query.service.impl;

import com.msa.order.domain.order.exception.OrderErrorCode;
import com.msa.order.domain.order.exception.OrderException;
import com.msa.order.domain.order.query.dto.response.CheckoutResponse;
import com.msa.order.domain.order.query.service.OrderStatusQueryService;
import com.msa.order.domain.order.repository.OrderRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/** 조회만으로 주문/재고 상태를 변경하지 않으며 행의 구매자·판매자 소유권을 검사합니다. */
@Service
@Transactional(readOnly = true)
public class OrderStatusQueryServiceImpl implements OrderStatusQueryService {
    private final OrderRepository orders;
    private final Clock clock;
    private final com.msa.order.domain.terms.query.service.CheckoutTermsService terms;

    public OrderStatusQueryServiceImpl(
            OrderRepository orders,
            Clock clock,
            com.msa.order.domain.terms.query.service.CheckoutTermsService terms) {
        this.terms = terms;
        this.orders = orders;
        this.clock = clock;
    }

    public CheckoutResponse buyer(UUID buyer, UUID id) {
        return get(buyer, id, false);
    }

    public CheckoutResponse seller(UUID seller, UUID id) {
        return get(seller, id, true);
    }

    private CheckoutResponse get(UUID actor, UUID id, boolean seller) {
        var o = orders.findById(id).orElseThrow(() -> new OrderException(OrderErrorCode.NOT_FOUND));
        if (!actor.equals(seller ? o.sellerId : o.buyerId)) {
            throw new OrderException(OrderErrorCode.OWNER);
        }
        return CheckoutResponse.from(
                o,
                clock.instant(),
                o.termsVersion == null ? terms.current().version() : o.termsVersion);
    }
}
