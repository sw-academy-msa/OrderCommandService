package com.msa.order.domain.order.client.dto.response;

/** Account 공통 응답 봉투에서 배송지 본문(data)만 읽습니다. HTTP 응답이 아닌 외부 수신용 DTO입니다. */
public record AddressEnvelope(AddressReply data) {
    @Override
    public String toString() {
        return "AddressEnvelope[REDACTED]";
    }
}
