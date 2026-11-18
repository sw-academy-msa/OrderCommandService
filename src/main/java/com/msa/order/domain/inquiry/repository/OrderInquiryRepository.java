package com.msa.order.domain.inquiry.repository;

import com.msa.order.domain.inquiry.entity.OrderInquiry;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** 주문 문의 원본만 관리하며 다른 서비스의 문의 테이블을 읽지 않습니다. */
public interface OrderInquiryRepository extends JpaRepository<OrderInquiry, UUID> {}
