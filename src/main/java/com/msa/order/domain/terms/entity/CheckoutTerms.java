package com.msa.order.domain.terms.entity;

import jakarta.persistence.*;

import org.hibernate.annotations.Immutable;

import java.time.Instant;

/** Flyway가 추가하는 버전별 약관 원본입니다. 기존 본문은 수정하지 않고 새 버전을 추가합니다. */
@Entity
@Immutable
@Table(name = "checkout_terms", schema = "orders")
public class CheckoutTerms {
    @Id
    @Column(length = 100)
    private String version;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(nullable = false)
    private Instant effectiveAt;

    @Column(nullable = false, length = 64)
    private String contentSha256;

    protected CheckoutTerms() {}

    public String getVersion() {
        return version;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public Instant getEffectiveAt() {
        return effectiveAt;
    }

    public String getContentSha256() {
        return contentSha256;
    }
}
