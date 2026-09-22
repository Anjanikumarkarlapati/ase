package com.bistrobyte.menuservice.controller;

import com.bistrobyte.menuservice.dto.StockReleaseRequest;
import com.bistrobyte.menuservice.dto.StockReservationRequest;
import com.bistrobyte.menuservice.dto.StockReservationResponse;
import com.bistrobyte.menuservice.service.MenuItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inter-service endpoints called by the order service while a basket is being confirmed.
 * Any authenticated principal may call them, because the caller is placing or cancelling
 * their own order; the menu service still enforces the stock rules itself.
 */
@RestController
@RequestMapping("/api/v1/menu/inventory")
@Tag(name = "Menu inventory", description = "Stock reservation and release for order placement")
public class MenuInventoryController {

    private final MenuItemService menuItemService;

    public MenuInventoryController(MenuItemService menuItemService) {
        this.menuItemService = menuItemService;
    }

    @PostMapping("/reserve")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Price a basket and hold stock",
            description = "All-or-nothing: if any line is sold out the whole reservation is rejected "
                    + "with HTTP 409 and no stock is consumed.")
    public ResponseEntity<StockReservationResponse> reserve(@Valid @RequestBody StockReservationRequest request) {
        return ResponseEntity.ok(menuItemService.reserve(request));
    }

    @PostMapping("/release")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Return reserved portions to stock after a cancellation")
    public ResponseEntity<Void> release(@Valid @RequestBody StockReleaseRequest request) {
        menuItemService.release(request);
        return ResponseEntity.noContent().build();
    }
}
