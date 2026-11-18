package com.msa.order.domain.terms.repository;

import com.msa.order.domain.terms.entity.CheckoutTerms;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.Optional;

/** 애플리케이션에는 읽기 계약만 제공하며 본문 등록과 변경 이력은 Flyway가 소유합니다. */
public interface CheckoutTermsRepository extends Repository<CheckoutTerms, String> {
    @Query("select t from CheckoutTerms t where t.version=:version")
    Optional<CheckoutTerms> findVersion(String version);
}
