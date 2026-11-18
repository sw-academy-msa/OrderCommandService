package com.msa.order.domain.withdrawal.service;

import com.msa.order.domain.withdrawal.dto.response.WithdrawalResponses.*;

import java.util.UUID;

/** Payment 원본 조회에 필요한 소속 확인된 결제 참조만 제공합니다. */
public interface WithdrawalStateService {
    State inspect(UUID account);

    Payments payments(UUID account, int page);
}
