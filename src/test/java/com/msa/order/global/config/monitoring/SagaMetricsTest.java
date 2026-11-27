package com.msa.order.global.config.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import com.msa.order.domain.order.repository.OrderRepository;
import com.msa.order.domain.order.saga.dto.response.SagaMetricsSnapshot;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;

/** 캐시 초기화와 수집 장애를 정상 0건으로 오인하지 않는지 검증합니다. */
class SagaMetricsTest {
    @Test
    void failureHidesStaleSnapshotAndRecoveryClearsMissingStates() {
        var orders = mock(OrderRepository.class);
        var row = mock(SagaMetricsSnapshot.class);
        Instant now = Instant.parse("2030-01-01T00:00:00Z");
        when(row.getState()).thenReturn("HELD");
        when(row.getOrders()).thenReturn(3L);
        when(row.getOldest()).thenReturn(now.minusSeconds(60));
        when(orders.monitoringSagaSnapshot()).thenReturn(List.of(row));
        var registry = new SimpleMeterRegistry();
        var metrics = new SagaMetricsConfig(orders, registry, Clock.fixed(now, ZoneOffset.UTC), true);
        assertThat(registry.get("stockdrop.saga.in.progress").gauge().value()).isNaN();
        metrics.sample();
        assertThat(registry.get("stockdrop.saga.in.progress").gauge().value()).isEqualTo(3);
        assertThat(registry.get("stockdrop.saga.oldest.in.progress.seconds").gauge().value()).isEqualTo(60);
        verify(orders, times(1)).monitoringSagaSnapshot();
        when(orders.monitoringSagaSnapshot()).thenThrow(new IllegalStateException());
        metrics.sample();
        assertThat(registry.get("stockdrop.saga.collector.available").gauge().value()).isZero();
        assertThat(registry.get("stockdrop.saga.in.progress").gauge().value()).isNaN();
        doReturn(List.of()).when(orders).monitoringSagaSnapshot();
        metrics.sample();
        assertThat(registry.get("stockdrop.saga.in.progress").gauge().value()).isZero();
        assertThat(registry.get("stockdrop.saga.oldest.in.progress.seconds").gauge().value()).isZero();
    }

    @Test
    void disabledMetricsNeverQueryDatabaseOrRegisterSeries() {
        var orders = mock(OrderRepository.class);
        var registry = new SimpleMeterRegistry();
        var metrics = new SagaMetricsConfig(orders, registry, Clock.systemUTC(), false);
        metrics.sample();
        verifyNoInteractions(orders);
        assertThat(registry.getMeters()).isEmpty();
    }
}
