package com.msa.order.domain.order.client.impl;

import com.msa.order.domain.order.client.*;
import com.msa.order.domain.order.client.dto.response.AddressEnvelope;
import com.msa.order.domain.order.client.dto.response.AddressReply;
import com.msa.order.domain.order.entity.ShippingAddress;
import com.msa.order.global.config.client.ClientsProperties;

import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** 저장할 배송지를 외부 SQL JOIN 없이 소유자 API로 가져옵니다. */
@Component
public class AddressClientImpl implements AddressClient {
    private final HttpTransport http;
    private final ClientsProperties properties;

    public AddressClientImpl(HttpTransport http, ClientsProperties properties) {
        this.http = http;
        this.properties = properties;
    }

    public ShippingAddress get(UUID id, String token) {
        // Account는 공통 응답 봉투로 감싸 응답하므로 data를 꺼내 읽습니다.
        var envelope =
                http.exchange(
                        properties.account(),
                        HttpMethod.GET,
                        "/api/v1/accounts/me/addresses/" + id,
                        null,
                        token,
                        AddressEnvelope.class);
        AddressReply reply = envelope.data();
        // 본문이 없거나 요청한 ID와 다르면 다른 배송지가 주문에 저장되지 않도록 차단합니다.
        if (reply == null || !id.equals(reply.addressId())) {
            throw new RemoteFailure("INVALID_ADDRESS_RESPONSE", false);
        }
        return reply.snapshot();
    }
}
