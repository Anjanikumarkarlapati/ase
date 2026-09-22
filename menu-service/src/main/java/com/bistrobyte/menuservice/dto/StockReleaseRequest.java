package com.bistrobyte.menuservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Compensating action: returns reserved portions to stock when an order is cancelled. */
public record StockReleaseRequest(

        @NotBlank(message = "An order reference is required for traceability")
        String orderReference,

        @NotEmpty(message = "At least one line is required")
        @Valid
        List<Line> lines) {

    public record Line(

            @NotNull(message = "menuItemId is required")
            Long menuItemId,

            @NotNull(message = "quantity is required")
            @Min(value = 1, message = "Quantity must be at least 1")
            Integer quantity) {
    }
}
