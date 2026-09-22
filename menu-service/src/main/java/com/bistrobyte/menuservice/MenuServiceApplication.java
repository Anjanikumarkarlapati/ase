package com.bistrobyte.menuservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Owns the digital menu: categories, dishes, pricing and live availability. Deliberately
 * decoupled from order processing so menu browsing stays up during a kitchen outage.
 */
@SpringBootApplication(scanBasePackages = "com.bistrobyte")
@EnableDiscoveryClient
public class MenuServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MenuServiceApplication.class, args);
    }
}
