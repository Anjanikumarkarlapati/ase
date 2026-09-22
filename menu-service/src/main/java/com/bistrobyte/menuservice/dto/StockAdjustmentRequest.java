package com.bistrobyte.menuservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Sets the remaining portions for a dish. Send {@code unlimited=true} to clear the cap
 * for dishes the kitchen can make to order.
 */
public record StockAdjustmentRequest(

        @PositiveOrZero(message = "Stock quantity cannot be negative")
        Integer stockQuantity,

        @NotNull(message = "unlimited is required")
        Boolean unlimited) {
}
