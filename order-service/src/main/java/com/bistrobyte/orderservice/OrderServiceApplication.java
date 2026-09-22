package com.bistrobyte.orderservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Owns order placement, kitchen execution and live tracking. Talks to the menu service
 * over Feign (resolved through Eureka) to price baskets and hold stock.
 */
@SpringBootApplication(scanBasePackages = "com.bistrobyte")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.bistrobyte.orderservice.client")
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
