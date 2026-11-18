package com.msa.order.domain.order.client;

import static org.assertj.core.api.Assertions.*;

import com.msa.order.global.config.client.*;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.*;
import org.springframework.http.HttpMethod;

import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** 내부 호출이 고정 주소 없이 서비스 ID 주소로 나가고, LoadBalancer가 고른 인스턴스로 전달되는지 실제 HTTP fixture로 확인합니다. */
class HttpTransportTest {
    final List<HttpServer> servers = new ArrayList<>();
    final ClientsProperties.Endpoint product =
            new ClientsProperties.Endpoint(true, "product-command-service", "");
    FixedLoadBalancer balancer;
    HttpTransport transport;

    @BeforeEach
    void setup() {
        balancer = new FixedLoadBalancer(Map.of());
        transport =
                new HttpTransport(
                        balancer.restClientBuilder().build(),
                        new ClientsProperties("http", null, null, null),
                        JsonMapper.builder().findAndAddModules().build());
    }

    @AfterEach
    void close() {
        servers.forEach(s -> s.stop(0));
    }

    @Test
    void unregisteredServiceFailsWithoutFallbackAddress() {
        AtomicInteger hits = new AtomicInteger();
        start("replica-a", hits);
        assertThatThrownBy(
                        () ->
                                transport.exchange(
                                        product, HttpMethod.GET, "/probe", null, "t", Map.class))
                .isInstanceOf(RemoteFailure.class)
                .hasMessage("SERVICE_UNAVAILABLE");
        assertThat(hits).hasValue(0);
    }

    @Test
    void choosesRegisteredInstanceOnEveryRequest() {
        AtomicInteger first = new AtomicInteger();
        AtomicInteger second = new AtomicInteger();
        balancer.register("product-command-service", start("replica-a", first));
        assertThat(transport.exchange(product, HttpMethod.GET, "/probe", null, "t", Map.class))
                .containsEntry("replica", "replica-a");
        // 첫 인스턴스가 목록에서 빠지고 복제본이 남은 상황입니다. 다음 요청은 재시작 없이 복제본으로 갑니다.
        balancer.register("product-command-service", start("replica-b", second));
        assertThat(transport.exchange(product, HttpMethod.GET, "/probe", null, "t", Map.class))
                .containsEntry("replica", "replica-b");
        assertThat(first).hasValue(1);
        assertThat(second).hasValue(1);
    }

    @Test
    void credentialsNeedHttpsUnlessLocalProfileAllowsPlaintext() {
        AtomicInteger hits = new AtomicInteger();
        balancer.register("account-service", start("account", hits));
        var account = new ClientsProperties.Endpoint(true, "account-service", "");
        var form = new org.springframework.util.LinkedMultiValueMap<String, String>();
        form.add("grant_type", "client_credentials");
        // 운영 설정(allowInsecureLocal=false)에서 http scheme이면 비밀값을 보내기 전에 차단합니다.
        assertThatThrownBy(
                        () ->
                                transport.exchangeServiceCredentials(
                                        account, form, "order-command-service", "synthetic", false))
                .isInstanceOf(RemoteFailure.class)
                .hasMessage("SERVICE_AUTH_TLS_REQUIRED");
        assertThat(hits).hasValue(0);
    }

    private String start(String name, AtomicInteger hits) {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext(
                    "/",
                    e -> {
                        hits.incrementAndGet();
                        byte[] b =
                                ("{\"replica\":\"" + name + "\"}")
                                        .getBytes(StandardCharsets.UTF_8);
                        e.getResponseHeaders().set("Content-Type", "application/json");
                        e.sendResponseHeaders(200, b.length);
                        e.getResponseBody().write(b);
                        e.close();
                    });
            server.start();
            servers.add(server);
            return "http://127.0.0.1:" + server.getAddress().getPort();
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }
}
