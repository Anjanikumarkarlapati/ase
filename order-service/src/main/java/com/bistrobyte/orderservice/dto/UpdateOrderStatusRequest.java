package com.bistrobyte.orderservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateOrderStatusRequest(

        @NotBlank(message = "Target status is required")
        String status,

        @Size(max = 255)
        String note) {
}
