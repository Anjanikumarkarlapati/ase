package com.bistrobyte.orderservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * A basket submitted for ordering. Channel-specific requirements (table number for
 * dine-in, address and phone for delivery) are enforced in the service layer, where the
 * message can name the channel that is missing them.
 */
public record PlaceOrderRequest(

        @NotNull(message = "Channel is required: DINE_IN, TAKEAWAY or DELIVERY")
        String channel,

        @NotEmpty(message = "An order must contain at least one item")
        @Size(max = 50, message = "An order may contain at most 50 lines")
        @Valid
        List<Line> items,

        @Size(max = 10)
        String tableNumber,

        @Size(max = 300)
        String deliveryAddress,

        @Pattern(regexp = "^$|^\\+?[0-9 -]{7,20}$", message = "Contact phone is not valid")
        String contactPhone,

        @Size(max = 500)
        String customerNote) {

    public record Line(

            @NotNull(message = "menuItemId is required")
            Long menuItemId,

            @NotNull(message = "quantity is required")
            @Min(value = 1, message = "Quantity must be at least 1")
            @Max(value = 50, message = "Quantity must be 50 or fewer per line")
            Integer quantity,

            @Size(max = 255)
            String specialInstructions) {
    }
}
