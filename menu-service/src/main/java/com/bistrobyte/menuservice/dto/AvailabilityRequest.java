package com.bistrobyte.menuservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Used by staff to 86 a dish mid-service, or to put it back on the menu. */
public record AvailabilityRequest(

        @NotNull(message = "available is required")
        Boolean available,

        @Size(max = 200)
        String reason) {
}
