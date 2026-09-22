package com.bistrobyte.orderservice.client;

import com.bistrobyte.orderservice.client.dto.StockReleaseCommand;
import com.bistrobyte.orderservice.client.dto.StockReservationCommand;
import com.bistrobyte.orderservice.client.dto.StockReservationResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Inter-service call to the menu service. The service id is resolved through Eureka, so
 * no host or port is hard-coded and the call is load balanced across instances.
 */
@FeignClient(name = "menu-service", path = "/api/v1/menu/inventory",
        configuration = FeignClientConfiguration.class)
public interface MenuServiceClient {

    /** Prices the basket and holds stock; fails with HTTP 409 if any line is unavailable. */
    @PostMapping("/reserve")
    StockReservationResult reserve(@RequestBody StockReservationCommand command);

    /** Compensating call issued when an order is cancelled. */
    @PostMapping("/release")
    void release(@RequestBody StockReleaseCommand command);
}
