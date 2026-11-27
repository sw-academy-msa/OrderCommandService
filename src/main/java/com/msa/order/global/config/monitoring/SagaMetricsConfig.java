package com.msa.order.global.config.monitoring;

import com.msa.order.domain.order.entity.PurchaseOrder.State;
import com.msa.order.domain.order.repository.OrderRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

/** 커밋된 상태 전이만 기록하고 공유 DB 집계는 scrape와 분리하여 캐시합니다. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SagaMetricsConfig {
    private final OrderRepository orders;
    private final MeterRegistry registry;
    private final Clock clock;
    private final boolean enabled;
    private volatile Map<State, Long> counts;
    private volatile Instant oldest;
    private volatile boolean available;

    public SagaMetricsConfig(OrderRepository orders, MeterRegistry registry, Clock clock,
            @Value("${monitoring.saga-enabled:false}") boolean enabled) {
        this.orders = orders;
        this.registry = registry;
        this.clock = clock;
        this.enabled = enabled;
        if (!enabled) return;
        for (State from : State.values()) {
            Gauge.builder("stockdrop.saga.orders", this, m -> m.count(from))
                    .tag("state", from.name()).register(registry);
            // enum 조합만 등록하여 첫 이벤트 이전의 0과 미배포를 구분합니다.
            for (State to : State.values()) if (from != to)
                registry.counter("stockdrop.saga.transitions", "from", from.name(), "to", to.name());
        }
        for (String step : new String[]{"PAYMENT_CANCEL", "STOCK_RELEASE"})
            registry.counter("stockdrop.saga.compensations", "step", step, "outcome", "success");
        Gauge.builder("stockdrop.saga.in.progress", this, m -> m.inProgress()).register(registry);
        Gauge.builder("stockdrop.saga.oldest.in.progress.seconds", this, m -> m.age()).register(registry);
        Gauge.builder("stockdrop.saga.collector.available", this, m -> m.available ? 1 : 0).register(registry);
    }

    /** 호출자는 주문 잠금과 실제 상태 변경으로 멱등성을 보장하며 롤백에서는 콜백이 실행되지 않습니다. */
    public void transition(State from, State to) {
        if (!enabled || from == to) return;
        afterCommit(() -> registry.counter("stockdrop.saga.transitions", "from", from.name(), "to", to.name()).increment());
    }

    /** 외부 취소 또는 해제 결과를 확인한 뒤 상태가 바뀌는 경우에만 완료로 계수합니다. */
    public void compensation(State from, State to) {
        if (!enabled || from == to) return;
        String step = from == State.COMPENSATING_PAYMENT && to == State.RELEASING_STOCK
                ? "PAYMENT_CANCEL"
                : (from == State.RELEASING_STOCK && (to == State.FAILED || to == State.EXPIRED)
                    || from == State.HELD && to == State.EXPIRED) ? "STOCK_RELEASE" : null;
        if (step != null) afterCommit(() -> registry.counter("stockdrop.saga.compensations", "step", step, "outcome", "success").increment());
    }

    private void afterCommit(Runnable action) {
        // 트랜잭션 없는 호출은 성공으로 추정하지 않습니다.
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { action.run(); }
        });
    }

    /** 실패 시 기존 snapshot을 보존하되 gauge는 NaN으로 숨겨 stale 정상값을 방지합니다. */
    @Scheduled(fixedDelayString = "${monitoring.saga-poll-ms:30000}",
            initialDelayString = "${monitoring.saga-poll-ms:30000}")
    public void sample() {
        if (!enabled) return;
        try {
            var next = new EnumMap<State, Long>(State.class);
            Instant nextOldest = null;
            for (var row : orders.monitoringSagaSnapshot()) {
                State state = State.valueOf(row.getState());
                next.put(state, row.getOrders());
                if (!terminal(state) && row.getOldest() != null
                        && (nextOldest == null || row.getOldest().isBefore(nextOldest))) nextOldest = row.getOldest();
            }
            oldest = nextOldest;
            counts = Map.copyOf(next);
            available = true;
        } catch (RuntimeException failure) {
            available = false;
            org.slf4j.LoggerFactory.getLogger(SagaMetricsConfig.class)
                    .warn("saga metrics unavailable cause={}", failure.getClass().getSimpleName());
        }
    }

    private static boolean terminal(State state) {
        return state == State.CONFIRMED || state == State.FAILED || state == State.EXPIRED;
    }
    private double count(State state) { return !available || counts == null ? Double.NaN : counts.getOrDefault(state, 0L); }
    private double inProgress() {
        if (!available || counts == null) return Double.NaN;
        return counts.entrySet().stream().filter(e -> !terminal(e.getKey())).mapToLong(Map.Entry::getValue).sum();
    }
    private double age() {
        if (!available || counts == null) return Double.NaN;
        return oldest == null ? 0 : Math.max(0, Duration.between(oldest, clock.instant()).getSeconds());
    }
}
