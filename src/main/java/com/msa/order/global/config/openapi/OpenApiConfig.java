package com.msa.order.global.config.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 주문·배송·문의 변경 계약입니다. 목록/일반 상세는 별도 Order Query 책임입니다. */
@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI orderCommandOpenApi() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("STOCKDROP Order Command")
                                .version("v1")
                                .description(
                                        "주문 생성·결제 Saga·판매자 배송·비공개 주문 문의. Payment 실제 구현과 서비스 토큰 발급"
                                                + " 계약은 별도 연결이 필요합니다."))
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        "bearerAuth",
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
