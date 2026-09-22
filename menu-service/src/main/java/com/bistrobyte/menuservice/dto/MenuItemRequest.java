package com.bistrobyte.menuservice.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record MenuItemRequest(

        @NotBlank(message = "Item name is required")
        @Size(max = 120)
        String name,

        @Size(max = 500)
        String description,

        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.01", message = "Price must be greater than zero")
        @DecimalMax(value = "99999.99", message = "Price is unrealistically high")
        @Digits(integer = 5, fraction = 2, message = "Price must have at most two decimal places")
        BigDecimal price,

        @NotNull(message = "Category is required")
        Long categoryId,

        Boolean available,

        /** {@code null} means "made to order" with no hard stock limit. */
        @PositiveOrZero(message = "Stock quantity cannot be negative")
        Integer stockQuantity,

        @Min(value = 1, message = "Preparation time must be at least one minute")
        @Max(value = 240, message = "Preparation time must be under four hours")
        Integer preparationMinutes,

        Boolean vegetarian,

        @Min(value = 0, message = "Spice level starts at 0")
        @Max(value = 3, message = "Spice level tops out at 3")
        Integer spiceLevel,

        @Size(max = 500)
        String imageUrl) {
}
