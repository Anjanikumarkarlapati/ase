package com.bistrobyte.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;

import static org.assertj.core.api.Assertions.assertThat;

/** The gateway wires up and exposes exactly the three platform routes. */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "eureka.client.register-with-eureka=false",
        "eureka.client.fetch-registry=false",
        "server.port=0"
})
class GatewayApplicationTests {

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void publishesPlatformRoutes() {
        assertThat(routeLocator.getRoutes().map(route -> route.getId()).collectList().block())
                .containsExactlyInAnyOrder("user-service", "menu-service", "order-service");
    }
}
