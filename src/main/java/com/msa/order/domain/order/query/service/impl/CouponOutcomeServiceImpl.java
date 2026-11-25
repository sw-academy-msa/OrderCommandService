package com.msa.order.domain.order.query.service.impl;

import com.msa.order.domain.order.exception.OrderErrorCode;
import com.msa.order.domain.order.exception.OrderException;
import com.msa.order.domain.order.query.dto.response.CouponOutcome;
import com.msa.order.domain.order.query.service.CouponOutcomeService;
import com.msa.order.domain.order.repository.OrderRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** 조회 실패나 진행 중 상태를 임의 종료로 바꾸지 않습니다. */
@Service
@Transactional(readOnly = true)
public class CouponOutcomeServiceImpl implements CouponOutcomeService {
    private final OrderRepository orders;

    public CouponOutcomeServiceImpl(OrderRepository orders) {
        this.orders = orders;
    }

    public CouponOutcome find(UUID id) {
        var o = orders.findById(id).orElseThrow(() -> new OrderException(OrderErrorCode.NOT_FOUND));
        return new CouponOutcome(
                o.id,
                o.state.name(),
                "LOCKED".equals(o.couponState),
                o.couponDiscount == null ? 0 : o.couponDiscount);
    }
}
