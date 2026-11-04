package com.msa.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 주문 원본·배송과 영속 Saga를 소유합니다. Order Query 및 Payment 애플리케이션과 분리합니다. */
@SpringBootApplication
public class OrderCommandApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderCommandApplication.class, args);
    }
}
