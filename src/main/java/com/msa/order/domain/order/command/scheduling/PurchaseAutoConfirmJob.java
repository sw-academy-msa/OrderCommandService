package com.msa.order.domain.order.command.scheduling;

import com.msa.order.domain.order.command.service.OrderWriteStore;
import com.msa.order.domain.order.repository.OrderRepository;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.*;

/**
 * 배송 완료 후 7일이 지나도록 구매자가 확정하지 않은 주문을 자동으로 구매 확정합니다.
 * 여러 인스턴스가 동시에 돌아도 저장소에서 행을 잠그고 다시 검사하므로 한 번만 확정됩니다.
 */
@Component
@ConditionalOnProperty(
        name = "order.purchase-confirmation.auto-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class PurchaseAutoConfirmJob {
    static final Duration AFTER_DELIVERY = Duration.ofDays(7);

    private final OrderRepository orders;
    private final OrderWriteStore store;
    private final Clock clock;

    public PurchaseAutoConfirmJob(OrderRepository orders, OrderWriteStore store, Clock clock) {
        this.orders = orders;
        this.store = store;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "${order.purchase-confirmation.poll-ms:600000}",
            scheduler = "purchaseConfirmScheduler")
    public void run() {
        var before = clock.instant().minus(AFTER_DELIVERY);
        for (var id : orders.autoConfirmIds(before, PageRequest.of(0, 100))) {
            try {
                store.autoConfirmPurchase(id, before);
            } catch (RuntimeException ex) {
                org.slf4j.LoggerFactory.getLogger(getClass())
                        .warn("자동 구매 확정 재시도 예정: type={}", ex.getClass().getSimpleName());
            }
        }
    }
}
