package com.bistrobyte.discovery;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "eureka.client.register-with-eureka=false",
        "eureka.client.fetch-registry=false",
        "server.port=0"
})
class DiscoveryServerApplicationTests {

    @Test
    void registryContextLoads() {
        // Verifies the Eureka server wiring starts cleanly.
    }
}
