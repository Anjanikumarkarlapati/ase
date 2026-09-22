package com.bistrobyte.menuservice.controller;

import com.bistrobyte.common.dto.PageResponse;
import com.bistrobyte.menuservice.dto.AvailabilityRequest;
import com.bistrobyte.menuservice.dto.MenuItemRequest;
import com.bistrobyte.menuservice.dto.MenuItemResponse;
import com.bistrobyte.menuservice.dto.StockAdjustmentRequest;
import com.bistrobyte.menuservice.service.MenuItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Customer-facing menu browsing plus staff and admin menu authoring. */
@RestController
@RequestMapping("/api/v1/menu/items")
@Tag(name = "Menu items", description = "Dishes, pricing and live availability")
public class MenuItemController {

    private final MenuItemService menuItemService;

    public MenuItemController(MenuItemService menuItemService) {
        this.menuItemService = menuItemService;
    }

    @GetMapping
    @Operation(summary = "Browse the menu",
            description = "Customers see orderable dishes by default; staff can pass includeInactive=true.")
    public ResponseEntity<PageResponse<MenuItemResponse>> search(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "true") boolean orderableOnly,
            @RequestParam(defaultValue = "false") boolean vegetarianOnly,
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sortBy) {
        return ResponseEntity.ok(PageResponse.of(
                menuItemService.search(categoryId, search, orderableOnly, vegetarianOnly, includeInactive,
                        PageRequest.of(page, Math.min(size, 100), Sort.by(sortBy))),
                item -> item));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a single dish")
    public ResponseEntity<MenuItemResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(menuItemService.getById(id));
    }

    @GetMapping("/batch")
    @Operation(summary = "Fetch several dishes at once",
            description = "Used by the order service to render order tickets in a single call.")
    public ResponseEntity<List<MenuItemResponse>> getByIds(@RequestParam List<Long> ids) {
        return ResponseEntity.ok(menuItemService.getByIds(ids));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @Operation(summary = "Add a dish to the menu")
    public ResponseEntity<MenuItemResponse> create(@Valid @RequestBody MenuItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(menuItemService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @Operation(summary = "Update a dish")
    public ResponseEntity<MenuItemResponse> update(@PathVariable Long id,
                                                   @Valid @RequestBody MenuItemRequest request) {
        return ResponseEntity.ok(menuItemService.update(id, request));
    }

    @PatchMapping("/{id}/availability")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF','KITCHEN')")
    @Operation(summary = "Mark a dish sold out, or put it back on the menu")
    public ResponseEntity<MenuItemResponse> setAvailability(@PathVariable Long id,
                                                            @Valid @RequestBody AvailabilityRequest request) {
        return ResponseEntity.ok(menuItemService.setAvailability(id, request));
    }

    @PatchMapping("/{id}/stock")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF','KITCHEN')")
    @Operation(summary = "Set the remaining portions for a dish")
    public ResponseEntity<MenuItemResponse> adjustStock(@PathVariable Long id,
                                                        @Valid @RequestBody StockAdjustmentRequest request) {
        return ResponseEntity.ok(menuItemService.adjustStock(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Retire a dish",
            description = "A soft delete: the dish leaves the menu but past orders still resolve it.")
    public ResponseEntity<MenuItemResponse> retire(@PathVariable Long id) {
        return ResponseEntity.ok(menuItemService.retire(id));
    }
}
