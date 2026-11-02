package com.msa.order.domain.order.client.impl;

import com.msa.order.domain.order.client.*;
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
        var reply =
                http.exchange(
                        properties.account(),
                        HttpMethod.GET,
                        "/api/v1/accounts/me/addresses/" + id,
                        null,
                        token,
                        AddressReply.class);
        if (!id.equals(reply.addressId())) {
            throw new RemoteFailure("INVALID_ADDRESS_RESPONSE", false);
        }
        return reply.snapshot();
    }
}
