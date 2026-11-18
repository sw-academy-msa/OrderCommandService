package com.msa.order.domain.order.command.service;

import com.msa.order.domain.order.command.dto.request.OrderRequests;
import com.msa.order.domain.order.entity.ShippingAddress;

import java.util.UUID;

/** Account HTTP 전후의 로컬 쓰기를 분리하며 사용자 Bearer는 저장하지 않습니다. */
public interface OrderWriteStore {
    UUID existing(UUID buyer, UUID requestKey, String hash);

    UUID create(UUID buyer, OrderRequests.Create request, String hash, ShippingAddress address);

    void address(UUID buyer, UUID id, OrderRequests.Address request, ShippingAddress address);

    void payment(UUID buyer, UUID id, OrderRequests.Payment request);

    void shipment(UUID seller, UUID id, OrderRequests.Shipment request);

    void deliver(UUID seller, UUID id, OrderRequests.Delivery request);
}
