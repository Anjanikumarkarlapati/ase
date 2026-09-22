package com.bistrobyte.userservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Owns accounts, roles and credential verification, and is the only service allowed to
 * mint JWTs for the platform.
 */
@SpringBootApplication(scanBasePackages = "com.bistrobyte")
@EnableDiscoveryClient
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
