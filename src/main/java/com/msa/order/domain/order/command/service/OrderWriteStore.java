package com.msa.order.domain.order.command.service;

import com.msa.order.domain.order.command.dto.request.OrderRequests;
import com.msa.order.domain.order.command.dto.response.MembershipBenefit;
import com.msa.order.domain.order.entity.ShippingAddress;

import java.util.UUID;

/** Account HTTP 전후의 로컬 쓰기를 분리하며 사용자 Bearer는 저장하지 않습니다. */
public interface OrderWriteStore {
    UUID existing(UUID buyer, UUID requestKey, String hash);

    UUID create(UUID buyer, OrderRequests.Create request, String hash, ShippingAddress address);

    UUID create(
            UUID buyer,
            OrderRequests.Create request,
            String hash,
            ShippingAddress address,
            MembershipBenefit membership);

    void address(UUID buyer, UUID id, OrderRequests.Address request, ShippingAddress address);

    void payment(UUID buyer, UUID id, OrderRequests.Payment request);

    void shipment(UUID seller, UUID id, OrderRequests.Shipment request);

    void deliver(UUID seller, UUID id, OrderRequests.Delivery request);

    /** 구매자 구매 확정(배송 완료 주문만). 이미 확정했으면 그대로 둔다 */
    void confirmPurchase(UUID buyer, UUID id);

    /** 자동 구매 확정: 잠근 뒤 다시 검사해 배송 완료가 before 이전이고 확정 전일 때만 확정한다 */
    void autoConfirmPurchase(UUID id, java.time.Instant before);
}
