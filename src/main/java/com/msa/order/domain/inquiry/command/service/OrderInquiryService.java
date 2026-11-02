package com.msa.order.domain.inquiry.command.service;

import com.msa.order.domain.inquiry.command.dto.request.InquiryRequests;
import com.msa.order.domain.inquiry.command.dto.response.InquiryResponse;

import java.util.UUID;

/** 주문 문의 생성·추가 문의·판매자 답변과 해당 작업용 조회를 소유합니다. */
public interface OrderInquiryService {
    InquiryResponse create(UUID buyer, UUID order, InquiryRequests.Create request);

    InquiryResponse message(
            UUID actor, UUID order, UUID inquiry, boolean seller, InquiryRequests.Message request);

    InquiryResponse get(UUID actor, UUID order, UUID inquiry, boolean seller);
}
