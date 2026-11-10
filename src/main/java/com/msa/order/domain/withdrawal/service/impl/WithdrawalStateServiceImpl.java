package com.msa.order.domain.withdrawal.service.impl;

import com.msa.order.domain.order.entity.PurchaseOrder;
import com.msa.order.domain.withdrawal.dto.response.WithdrawalResponses.*;
import com.msa.order.domain.withdrawal.repository.WithdrawalStateRepository;
import com.msa.order.domain.withdrawal.service.WithdrawalStateService;
import com.msa.order.global.error.*;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** 대량 이력을 잘라서 탈퇴 가능으로 판단하지 않도록 100건 페이지와 hasNext를 제공합니다. */
@Service
@Transactional(readOnly = true)
public class WithdrawalStateServiceImpl implements WithdrawalStateService {
    private final WithdrawalStateRepository state;

    public WithdrawalStateServiceImpl(WithdrawalStateRepository state) {
        this.state = state;
    }

    public State inspect(UUID account) {
        return State.builder()
                .activeOrders(state.activeOrders(account))
                // 수수료는 후속 범위이며 완료된 판매 이력만으로 탈퇴를 차단하지 않습니다.
                // 기존 내부 응답 필드는 유지하고 실제 미확정 결제는 Payment 원본에서 별도로 검증합니다.
                .pendingSettlements(0L)
                .build();
    }

    public Payments payments(UUID account, int page) {
        if (page < 0 || (long) page * 100 > Integer.MAX_VALUE)
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        var rows = state.payments(account, PageRequest.of(page, 100));
        return Payments.builder()
                .references(
                        rows.stream()
                                .map(
                                        o ->
                                                PaymentReference.builder()
                                                        .orderId(o.id)
                                                        .paymentAttemptId(o.paymentAttemptId)
                                                        .approved(
                                                                o.state
                                                                        == PurchaseOrder.State
                                                                                .CONFIRMED)
                                                        .build())
                                .toList())
                .hasNext(rows.hasNext())
                .build();
    }
}
