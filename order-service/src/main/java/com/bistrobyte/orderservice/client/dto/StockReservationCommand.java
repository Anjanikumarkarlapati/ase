package com.bistrobyte.orderservice.client.dto;

import java.util.List;

/** Mirror of the menu service reservation contract, owned by this service on purpose. */
public record StockReservationCommand(String orderReference, List<Line> lines) {

    public record Line(Long menuItemId, Integer quantity) {
    }
}
