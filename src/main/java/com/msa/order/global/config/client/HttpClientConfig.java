package com.msa.order.global.config.client;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/** HTTP 대기시간을 제한하고 리다이렉트로 서비스 토큰이 다른 호스트에 유출되는 것을 막습니다. */
@Configuration
@EnableConfigurationProperties({
    ClientsProperties.class,
    com.msa.order.global.config.security.ServiceTokenProperties.class,
    com.msa.order.global.config.checkout.CheckoutProperties.class
})
public class HttpClientConfig {
    @Bean
    public RestClient internalRestClient() {
        var http =
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(2))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(3));
        return RestClient.builder().requestFactory(factory).build();
    }
}
