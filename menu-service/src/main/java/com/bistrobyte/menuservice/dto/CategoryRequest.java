package com.bistrobyte.menuservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CategoryRequest(

        @NotBlank(message = "Category name is required")
        @Size(max = 80)
        String name,

        @Size(max = 255)
        String description,

        @PositiveOrZero(message = "Display order cannot be negative")
        Integer displayOrder,

        Boolean active) {
}
