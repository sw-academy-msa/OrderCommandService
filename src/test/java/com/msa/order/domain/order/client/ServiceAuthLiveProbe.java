package com.msa.order.domain.order.client;

import com.msa.order.domain.order.client.impl.*;
import com.msa.order.global.config.client.*;
import com.msa.order.global.config.security.*;

import org.springframework.web.client.RestClient;

import tools.jackson.databind.json.JsonMapper;

import java.io.*;
import java.time.*;
import java.util.*;

/** 로컬 실제 Account/Product 검증 도구입니다. stdin 자격증명은 파일/인자/로그에 남기지 않습니다. */
public class ServiceAuthLiveProbe {
    public static void main(String[] args) {
        try {
            var input =
                    new BufferedReader(
                            new InputStreamReader(
                                    System.in, java.nio.charset.StandardCharsets.UTF_8));
            String secret = input.readLine();
            UUID subject = UUID.fromString(input.readLine());
            String issuer = input.readLine();
            String accountUrl = input.readLine();
            String productUrl = input.readLine();
            var account = new ClientsProperties.Endpoint(true, "account-service", "");
            var product = new ClientsProperties.Endpoint(true, "product-command-service", "");
            var clients = new ClientsProperties("http", product, account, account);
            var rest = RestClient.builder().requestFactory(HttpClientConfig.requestFactory()).build();
            // 운영과 같은 서비스 ID 선택 경로를 쓰되, 입력받은 로컬 주소를 등록 정보 대신 사용합니다.
            var balancer =
                    new FixedLoadBalancer(
                            Map.of(
                                    "account-service", accountUrl,
                                    "product-command-service", productUrl));
            var http =
                    new HttpTransport(
                            balancer.restClientBuilder().build(),
                            clients,
                            JsonMapper.builder().findAndAddModules().build());
            var clock = Clock.systemUTC();
            var decoder =
                    new JwtConfig()
                            .jwtDecoder(
                                    new AuthProperties(
                                            issuer,
                                            "stockdrop-api",
                                            accountUrl + "/.well-known/jwks.json"),
                                    clock);
            var provider =
                    new ServiceTokenProviderImpl(
                            http,
                            clients,
                            new ServiceTokenProperties(
                                    true,
                                    "order-command-service",
                                    secret,
                                    subject,
                                    Duration.ofSeconds(30),
                                    true),
                            decoder,
                            clock);
            String token = provider.token("product.stock");
            if (!token.equals(provider.token("product.stock"))) {
                throw new IllegalStateException("캐시 검증 실패");
            }
            try {
                new ProductClientImpl(http, clients, provider).get(UUID.randomUUID());
                throw new IllegalStateException("미존재 확보 응답이 아닙니다.");
            } catch (RemoteFailure response) {
                if (!response.definitive() || !"PRODUCT_NOT_FOUND".equals(response.code())) {
                    throw response;
                }
            }
            int userStatus =
                    rest.get()
                            .uri(accountUrl + "/api/v1/accounts/me")
                            .headers(h -> h.setBearerAuth(token))
                            .exchange((request, response) -> response.getStatusCode().value());
            if (userStatus != 401) {
                throw new IllegalStateException("서비스 JWT 사용자 접근 차단 검증 실패");
            }
            System.out.println(
                    "PASS: existing Account issuer -> Order token cache -> Product scope/subject;"
                            + " Account user API rejects service JWT.");
        } catch (Exception failure) {
            System.err.println("Service auth live probe failed. Secret/token/error body withheld.");
            System.exit(1);
        }
    }
}
