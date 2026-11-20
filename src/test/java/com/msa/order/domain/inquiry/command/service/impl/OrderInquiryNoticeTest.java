package com.msa.order.domain.inquiry.command.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.msa.order.domain.image.repository.InquiryImageRepository;
import com.msa.order.domain.inquiry.command.dto.request.InquiryRequests.*;
import com.msa.order.domain.inquiry.entity.OrderInquiry;
import com.msa.order.domain.inquiry.repository.OrderInquiryRepository;
import com.msa.order.domain.notification.service.BusinessNoticeService;
import com.msa.order.domain.order.entity.PurchaseOrder;
import com.msa.order.domain.order.repository.OrderRepository;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import tools.jackson.databind.json.JsonMapper;

/** 신규 질문과 답변은 상대방에게, 동일 requestKey 재시도는 발행 없이 반환하는지 검증합니다. */
class OrderInquiryNoticeTest {
    OrderRepository orders = mock(OrderRepository.class);
    OrderInquiryRepository inquiries = mock(OrderInquiryRepository.class);
    InquiryImageRepository images = mock(InquiryImageRepository.class);
    EntityManager em = mock(EntityManager.class);
    BusinessNoticeService notices = mock(BusinessNoticeService.class);
    Instant now = Instant.parse("2030-01-01T00:00:00Z");
    UUID buyer = UUID.randomUUID(), seller = UUID.randomUUID(), orderId = UUID.randomUUID();
    PurchaseOrder order = PurchaseOrder.builder().buyer(buyer).key(UUID.randomUUID())
            .hash("hash").product(UUID.randomUUID()).now(now).build();
    Map<UUID, OrderInquiry> stored = new HashMap<>();
    OrderInquiryServiceImpl service = new OrderInquiryServiceImpl(orders, inquiries, images, em,
            JsonMapper.builder().findAndAddModules().build(), Clock.fixed(now, ZoneOffset.UTC), notices);

    @BeforeEach
    void setup() {
        order.id = orderId;
        order.sellerId = seller;
        order.state = PurchaseOrder.State.CONFIRMED;
        when(orders.lock(orderId)).thenReturn(Optional.of(order));
        when(inquiries.findById(any())).thenAnswer(i -> Optional.ofNullable(stored.get(i.getArgument(0))));
        when(inquiries.saveAndFlush(any())).thenAnswer(i -> {
            OrderInquiry value = i.getArgument(0); stored.put(value.id, value); return value;
        });
    }

    @Test
    void creationFollowupAndAnswerNotifyCounterpartyAndRetriesDoNot() {
        var create = new Create(UUID.randomUUID(), "배송", "첫 질문", List.of());
        UUID id = service.create(buyer, orderId, create).id();
        service.create(buyer, orderId, create);
        verify(notices).enqueue(seller, "ORDER_INQUIRY_CREATED", id, 1,
                "SELLER_ORDER_INQUIRY", id, orderId, null);
        var answer = new Message(UUID.randomUUID(), "답변");
        service.message(seller, orderId, id, true, answer);
        service.message(seller, orderId, id, true, answer);
        verify(notices).enqueue(buyer, "ORDER_INQUIRY_ANSWERED", id, 2,
                "ORDER_INQUIRY", id, orderId, null);
        var followup = new Message(UUID.randomUUID(), "추가 질문");
        service.message(buyer, orderId, id, false, followup);
        service.message(buyer, orderId, id, false, followup);
        verify(notices).enqueue(seller, "ORDER_INQUIRY_CREATED", id, 3,
                "SELLER_ORDER_INQUIRY", id, orderId, null);
        verifyNoMoreInteractions(notices);
        assertThatThrownBy(() -> service.message(buyer, orderId, id, false,
                new Message(followup.requestKey(), "다른 내용"))).isInstanceOf(RuntimeException.class);
        verifyNoMoreInteractions(notices);
    }

    @Test
    void selfInquiryAndAnswerDoNotNotify() {
        order.sellerId = buyer;
        UUID id = service.create(buyer, orderId,
                new Create(UUID.randomUUID(), "상품", "질문", List.of())).id();
        service.message(buyer, orderId, id, true, new Message(UUID.randomUUID(), "답변"));
        verifyNoInteractions(notices);
    }

    @Test
    void unauthorizedCreationDoesNotNotify() {
        assertThatThrownBy(() -> service.create(UUID.randomUUID(), orderId,
                new Create(UUID.randomUUID(), "배송", "질문", List.of())))
                .isInstanceOf(RuntimeException.class);
        verifyNoInteractions(notices);
    }
}
