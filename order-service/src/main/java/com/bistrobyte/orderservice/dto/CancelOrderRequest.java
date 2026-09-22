package com.bistrobyte.orderservice.dto;

import jakarta.validation.constraints.Size;

public record CancelOrderRequest(

        @Size(max = 255, message = "Reason must be 255 characters or fewer")
        String reason) {
}
