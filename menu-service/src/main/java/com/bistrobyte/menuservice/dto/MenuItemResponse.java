package com.bistrobyte.menuservice.dto;

import com.bistrobyte.menuservice.domain.MenuItem;

import java.math.BigDecimal;
import java.time.Instant;

public record MenuItemResponse(Long id,
                               String name,
                               String description,
                               BigDecimal price,
                               Long categoryId,
                               String categoryName,
                               boolean available,
                               Integer stockQuantity,
                               boolean orderable,
                               int preparationMinutes,
                               boolean vegetarian,
                               int spiceLevel,
                               String imageUrl,
                               boolean active,
                               Instant updatedAt) {

    public static MenuItemResponse from(MenuItem item) {
        return new MenuItemResponse(
                item.getId(),
                item.getName(),
                item.getDescription(),
                item.getPrice(),
                item.getCategory() == null ? null : item.getCategory().getId(),
                item.getCategory() == null ? null : item.getCategory().getName(),
                item.isAvailable(),
                item.getStockQuantity(),
                item.isOrderable(),
                item.getPreparationMinutes(),
                item.isVegetarian(),
                item.getSpiceLevel(),
                item.getImageUrl(),
                item.isActive(),
                item.getUpdatedAt());
    }
}
