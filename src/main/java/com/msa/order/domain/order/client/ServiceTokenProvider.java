package com.msa.order.domain.order.client;

/** 짧은 서비스 JWT를 인스턴스 메모리에 캐시하고 만료 전에 자격증명으로 다시 발급받습니다. */
public interface ServiceTokenProvider {
    String token(String scope);
}
