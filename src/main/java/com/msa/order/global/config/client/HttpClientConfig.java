package com.msa.order.global.config.client;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.*;
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
    /**
     * 내부 서비스 호출 전용입니다. Spring Cloud LoadBalancer 인터셉터가 서비스 ID를 Eureka 인스턴스로 바꾸고 상태 확인과 연결 실패
     * 재시도를 적용합니다. RestClient.Builder를 빈으로 두면 Eureka 클라이언트도 그 Builder로 레지스트리를 조회하다 서비스 ID 변환에
     * 막혀 기동이 멈추므로, 인터셉터는 이 클라이언트에만 직접 붙입니다.
     */
    @Bean
    public RestClient loadBalancedRestClient(
            ObjectProvider<BlockingLoadBalancerInterceptor> interceptor) {
        return RestClient.builder()
                .requestFactory(requestFactory())
                .requestInterceptor(new DeferringLoadBalancerInterceptor(interceptor))
                .build();
    }

    /** LoadBalancer 상태 확인이 실제 인스턴스 주소를 직접 호출할 때 쓰는 일반 클라이언트입니다. 서비스 ID 변환을 거치지 않습니다. */
    @Bean
    @Primary
    public RestClient healthCheckRestClient() {
        return RestClient.builder().requestFactory(requestFactory()).build();
    }

    /** 연결 2초·응답 3초 제한이며 리다이렉트를 따라가지 않습니다. */
    public static JdkClientHttpRequestFactory requestFactory() {
        var http =
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(2))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(3));
        return factory;
    }
}
