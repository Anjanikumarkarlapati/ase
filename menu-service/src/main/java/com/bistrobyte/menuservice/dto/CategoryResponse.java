package com.bistrobyte.menuservice.dto;

import com.bistrobyte.menuservice.domain.Category;

public record CategoryResponse(Long id,
                               String name,
                               String description,
                               int displayOrder,
                               boolean active,
                               long itemCount) {

    public static CategoryResponse from(Category category, long itemCount) {
        return new CategoryResponse(category.getId(), category.getName(), category.getDescription(),
                category.getDisplayOrder(), category.isActive(), itemCount);
    }
}
