package com.msa.order.domain.order.client;

import com.msa.order.domain.order.entity.ShippingAddress;

import java.util.UUID;

/** 실제 Account API를 요청자의 Bearer로 호출합니다. 사용자 토큰은 DB/작업 큐에 보관하지 않습니다. */
public interface AddressClient {
    ShippingAddress get(UUID addressId, String accessToken);
}
