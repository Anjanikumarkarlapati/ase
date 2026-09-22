package com.bistrobyte.orderservice.client.dto;

import java.util.List;

public record StockReleaseCommand(String orderReference, List<Line> lines) {

    public record Line(Long menuItemId, Integer quantity) {
    }
}
