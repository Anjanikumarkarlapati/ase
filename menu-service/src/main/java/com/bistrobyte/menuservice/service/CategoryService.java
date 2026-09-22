package com.bistrobyte.menuservice.service;

import com.bistrobyte.common.exception.BusinessRuleException;
import com.bistrobyte.common.exception.DuplicateResourceException;
import com.bistrobyte.common.exception.ResourceNotFoundException;
import com.bistrobyte.menuservice.domain.Category;
import com.bistrobyte.menuservice.dto.CategoryRequest;
import com.bistrobyte.menuservice.dto.CategoryResponse;
import com.bistrobyte.menuservice.repository.CategoryRepository;
import com.bistrobyte.menuservice.repository.MenuItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final MenuItemRepository menuItemRepository;

    public CategoryService(CategoryRepository categoryRepository, MenuItemRepository menuItemRepository) {
        this.categoryRepository = categoryRepository;
        this.menuItemRepository = menuItemRepository;
    }

    public List<CategoryResponse> list(boolean includeInactive) {
        List<Category> categories = includeInactive
                ? categoryRepository.findAllByOrderByDisplayOrderAscNameAsc()
                : categoryRepository.findByActiveTrueOrderByDisplayOrderAscNameAsc();
        return categories.stream().map(this::toResponse).toList();
    }

    public CategoryResponse getById(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        if (categoryRepository.existsByNameIgnoreCase(request.name().trim())) {
            throw new DuplicateResourceException("A category named '" + request.name() + "' already exists");
        }
        Category category = new Category();
        apply(category, request);
        return toResponse(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = findOrThrow(id);
        Optional<Category> nameOwner = categoryRepository.findByNameIgnoreCase(request.name().trim());
        if (nameOwner.isPresent() && !nameOwner.get().getId().equals(id)) {
            throw new DuplicateResourceException("A category named '" + request.name() + "' already exists");
        }
        apply(category, request);
        return toResponse(categoryRepository.save(category));
    }

    /** Refuses to delete a section that still holds live dishes; deactivate it instead. */
    @Transactional
    public void delete(Long id) {
        Category category = findOrThrow(id);
        long liveItems = menuItemRepository.countByCategoryIdAndActiveTrue(id);
        if (liveItems > 0) {
            throw new BusinessRuleException("Category '" + category.getName() + "' still holds " + liveItems
                    + " active item(s). Move or retire them first, or deactivate the category.");
        }
        categoryRepository.delete(category);
    }

    /** Resolves a category for the item service, rejecting retired sections. */
    Category requireActive(Long id) {
        Category category = findOrThrow(id);
        if (!category.isActive()) {
            throw new BusinessRuleException("Category '" + category.getName() + "' is not active");
        }
        return category;
    }

    private Category findOrThrow(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
    }

    private void apply(Category category, CategoryRequest request) {
        category.setName(request.name().trim());
        category.setDescription(request.description());
        if (request.displayOrder() != null) {
            category.setDisplayOrder(request.displayOrder());
        }
        if (request.active() != null) {
            category.setActive(request.active());
        }
    }

    private CategoryResponse toResponse(Category category) {
        return CategoryResponse.from(category, menuItemRepository.countByCategoryIdAndActiveTrue(category.getId()));
    }
}
