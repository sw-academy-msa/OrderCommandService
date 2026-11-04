package com.msa.order.domain.order.command.service;

import com.msa.order.domain.order.command.dto.request.OrderRequests;
import com.msa.order.domain.order.query.dto.response.CheckoutResponse;

import java.util.UUID;

/** 구매자 주문 변경과 판매자의 배송 변경을 소유합니다. */
public interface OrderCommandService {
    CheckoutResponse create(UUID buyer, OrderRequests.Create request, String accessToken);

    CheckoutResponse address(
            UUID buyer, UUID order, OrderRequests.Address request, String accessToken);

    CheckoutResponse payment(UUID buyer, UUID order, OrderRequests.Payment request);

    CheckoutResponse shipment(UUID seller, UUID order, OrderRequests.Shipment request);

    CheckoutResponse deliver(UUID seller, UUID order, OrderRequests.Delivery request);
}
