package com.bistrobyte.menuservice.service;

import com.bistrobyte.common.exception.BusinessRuleException;
import com.bistrobyte.common.exception.DuplicateResourceException;
import com.bistrobyte.common.exception.ResourceNotFoundException;
import com.bistrobyte.menuservice.domain.Category;
import com.bistrobyte.menuservice.domain.MenuItem;
import com.bistrobyte.menuservice.dto.AvailabilityRequest;
import com.bistrobyte.menuservice.dto.MenuItemRequest;
import com.bistrobyte.menuservice.dto.MenuItemResponse;
import com.bistrobyte.menuservice.dto.StockAdjustmentRequest;
import com.bistrobyte.menuservice.dto.StockReleaseRequest;
import com.bistrobyte.menuservice.dto.StockReservationRequest;
import com.bistrobyte.menuservice.dto.StockReservationResponse;
import com.bistrobyte.menuservice.repository.MenuItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Menu authoring, live availability and the stock reservation used by order placement. */
@Service
@Transactional(readOnly = true)
public class MenuItemService {

    private static final Logger log = LoggerFactory.getLogger(MenuItemService.class);

    private final MenuItemRepository repository;
    private final CategoryService categoryService;

    public MenuItemService(MenuItemRepository repository, CategoryService categoryService) {
        this.repository = repository;
        this.categoryService = categoryService;
    }

    public Page<MenuItemResponse> search(Long categoryId,
                                         String search,
                                         boolean orderableOnly,
                                         boolean vegetarianOnly,
                                         boolean includeInactive,
                                         Pageable pageable) {
        String term = (search == null || search.isBlank()) ? null : search.trim();
        return repository.search(categoryId, term, orderableOnly, vegetarianOnly, includeInactive, pageable)
                .map(MenuItemResponse::from);
    }

    public MenuItemResponse getById(Long id) {
        return MenuItemResponse.from(findOrThrow(id));
    }

    /** Bulk lookup so the order service can render a ticket in one call. */
    public List<MenuItemResponse> getByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return repository.findByIdInAndActiveTrue(ids).stream().map(MenuItemResponse::from).toList();
    }

    @Transactional
    public MenuItemResponse create(MenuItemRequest request) {
        Category category = categoryService.requireActive(request.categoryId());
        if (repository.existsByNameIgnoreCaseAndCategoryId(request.name().trim(), category.getId())) {
            throw new DuplicateResourceException(
                    "'" + request.name() + "' already exists in category '" + category.getName() + "'");
        }
        MenuItem item = new MenuItem();
        item.setCategory(category);
        apply(item, request);
        MenuItem saved = repository.save(item);
        log.info("Created menu item id={} name='{}'", saved.getId(), saved.getName());
        return MenuItemResponse.from(saved);
    }

    @Transactional
    public MenuItemResponse update(Long id, MenuItemRequest request) {
        MenuItem item = findOrThrow(id);
        Category category = categoryService.requireActive(request.categoryId());
        boolean renamedIntoClash = !item.getName().equalsIgnoreCase(request.name().trim())
                || !category.getId().equals(item.getCategory().getId());
        if (renamedIntoClash
                && repository.existsByNameIgnoreCaseAndCategoryId(request.name().trim(), category.getId())) {
            throw new DuplicateResourceException(
                    "'" + request.name() + "' already exists in category '" + category.getName() + "'");
        }
        item.setCategory(category);
        apply(item, request);
        return MenuItemResponse.from(repository.save(item));
    }

    /** Staff "86" toggle. Does not touch stock counts. */
    @Transactional
    public MenuItemResponse setAvailability(Long id, AvailabilityRequest request) {
        MenuItem item = findOrThrow(id);
        if (!item.isActive()) {
            throw new BusinessRuleException("'" + item.getName() + "' is retired and cannot be put back on sale");
        }
        item.setAvailable(request.available());
        log.info("Menu item id={} marked {}{}", id, request.available() ? "available" : "sold out",
                request.reason() == null ? "" : " (" + request.reason() + ")");
        return MenuItemResponse.from(repository.save(item));
    }

    @Transactional
    public MenuItemResponse adjustStock(Long id, StockAdjustmentRequest request) {
        MenuItem item = findOrThrow(id);
        if (Boolean.TRUE.equals(request.unlimited())) {
            item.setStockQuantity(null);
        } else {
            if (request.stockQuantity() == null) {
                throw new BusinessRuleException(
                        "stockQuantity is required unless the item is marked unlimited");
            }
            item.setStockQuantity(request.stockQuantity());
            // Restocking a sold-out dish puts it straight back on the menu.
            if (request.stockQuantity() > 0 && item.isActive()) {
                item.setAvailable(true);
            }
        }
        return MenuItemResponse.from(repository.save(item));
    }

    /** Soft delete: the dish leaves the menu but historical orders still resolve it. */
    @Transactional
    public MenuItemResponse retire(Long id) {
        MenuItem item = findOrThrow(id);
        item.setActive(false);
        item.setAvailable(false);
        return MenuItemResponse.from(repository.save(item));
    }

    /**
     * Validates, prices and holds stock for an entire basket in one transaction. Any
     * unorderable line rolls the whole reservation back, so a customer can never be charged
     * for a sold-out dish.
     */
    @Transactional
    public StockReservationResponse reserve(StockReservationRequest request) {
        Map<Long, Integer> quantities = mergeLines(
                request.lines(), StockReservationRequest.Line::menuItemId, StockReservationRequest.Line::quantity);

        List<String> problems = new ArrayList<>();
        List<StockReservationResponse.ReservedLine> reserved = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        int longestPrep = 0;

        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            Long itemId = entry.getKey();
            int quantity = entry.getValue();
            MenuItem item = repository.findByIdForUpdate(itemId).orElse(null);
            if (item == null) {
                problems.add("Menu item " + itemId + " does not exist");
                continue;
            }
            if (!item.isActive()) {
                problems.add("'" + item.getName() + "' is no longer on the menu");
                continue;
            }
            if (!item.isAvailable()) {
                problems.add("'" + item.getName() + "' is sold out for today");
                continue;
            }
            if (!item.canFulfil(quantity)) {
                problems.add("'" + item.getName() + "' has only " + item.getStockQuantity()
                        + " portion(s) left but " + quantity + " were requested");
                continue;
            }

            if (item.getStockQuantity() != null) {
                int remaining = item.getStockQuantity() - quantity;
                item.setStockQuantity(remaining);
                if (remaining == 0) {
                    // Auto-86 so the next customer never sees it as orderable.
                    item.setAvailable(false);
                }
                repository.save(item);
            }

            BigDecimal lineTotal = item.getPrice().multiply(BigDecimal.valueOf(quantity));
            subtotal = subtotal.add(lineTotal);
            longestPrep = Math.max(longestPrep, item.getPreparationMinutes());
            reserved.add(new StockReservationResponse.ReservedLine(
                    item.getId(), item.getName(), item.getPrice(), quantity, lineTotal,
                    item.getPreparationMinutes()));
        }

        if (!problems.isEmpty()) {
            // Rolls back every decrement performed above.
            throw new BusinessRuleException("Order cannot be placed: " + String.join("; ", problems));
        }

        reserved.sort(Comparator.comparing(StockReservationResponse.ReservedLine::menuItemId));
        log.info("Reserved {} line(s) for order {}", reserved.size(), request.orderReference());
        return new StockReservationResponse(request.orderReference(), reserved, subtotal, longestPrep);
    }

    /** Compensating action for a cancelled order: portions go back on the shelf. */
    @Transactional
    public void release(StockReleaseRequest request) {
        Map<Long, Integer> quantities = mergeLines(
                request.lines(), StockReleaseRequest.Line::menuItemId, StockReleaseRequest.Line::quantity);
        quantities.forEach((itemId, quantity) -> repository.findByIdForUpdate(itemId).ifPresent(item -> {
            if (item.getStockQuantity() != null) {
                item.setStockQuantity(item.getStockQuantity() + quantity);
                if (item.isActive()) {
                    item.setAvailable(true);
                }
                repository.save(item);
            }
        }));
        log.info("Released {} line(s) back to stock for order {}",
                quantities.size(), request.orderReference());
    }

    /** Collapses duplicate lines for the same dish so stock is checked once per item. */
    private <L> Map<Long, Integer> mergeLines(List<L> lines,
                                              java.util.function.Function<L, Long> idExtractor,
                                              java.util.function.Function<L, Integer> quantityExtractor) {
        Map<Long, Integer> merged = new LinkedHashMap<>();
        for (L line : lines) {
            merged.merge(idExtractor.apply(line), quantityExtractor.apply(line), Integer::sum);
        }
        return merged;
    }

    private MenuItem findOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Menu item", id));
    }

    private void apply(MenuItem item, MenuItemRequest request) {
        item.setName(request.name().trim());
        item.setDescription(request.description());
        item.setPrice(request.price());
        item.setImageUrl(request.imageUrl());
        if (request.available() != null) {
            item.setAvailable(request.available());
        }
        item.setStockQuantity(request.stockQuantity());
        if (request.preparationMinutes() != null) {
            item.setPreparationMinutes(request.preparationMinutes());
        }
        if (request.vegetarian() != null) {
            item.setVegetarian(request.vegetarian());
        }
        if (request.spiceLevel() != null) {
            item.setSpiceLevel(request.spiceLevel());
        }
        item.setActive(true);
    }
}
