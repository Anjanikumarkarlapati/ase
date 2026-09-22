package com.bistrobyte.orderservice.controller;

import com.bistrobyte.common.dto.PageResponse;
import com.bistrobyte.common.security.AuthenticatedUser;
import com.bistrobyte.common.security.SecurityUtils;
import com.bistrobyte.orderservice.domain.OrderChannel;
import com.bistrobyte.orderservice.domain.OrderStatus;
import com.bistrobyte.orderservice.dto.CancelOrderRequest;
import com.bistrobyte.orderservice.dto.OrderResponse;
import com.bistrobyte.orderservice.dto.OrderTrackingResponse;
import com.bistrobyte.orderservice.dto.PlaceOrderRequest;
import com.bistrobyte.orderservice.dto.UpdateOrderStatusRequest;
import com.bistrobyte.orderservice.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/** Customer ordering, staff console and kitchen execution endpoints. */
@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders", description = "Placement, tracking and kitchen execution")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CUSTOMER','STAFF','ADMIN')")
    @Operation(summary = "Place an order",
            description = "Reserves stock in the menu service first. If any line is sold out the "
                    + "order is rejected with HTTP 409 and nothing is charged or reserved.")
    public ResponseEntity<OrderResponse> place(@Valid @RequestBody PlaceOrderRequest request) {
        OrderResponse created = orderService.place(request, SecurityUtils.requireCurrentUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    @Operation(summary = "List orders",
            description = "Staff, kitchen and admin see every ticket; customers only ever see their own.")
    public ResponseEntity<PageResponse<OrderResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) Long customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        Long effectiveCustomerId = SecurityUtils.isStaffLevel() ? customerId : caller.userId();
        return ResponseEntity.ok(PageResponse.of(
                orderService.search(
                        effectiveCustomerId,
                        status == null || status.isBlank() ? null : OrderStatus.from(status),
                        channel == null || channel.isBlank() ? null : parseChannel(channel),
                        from, to,
                        PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "placedAt"))),
                order -> order));
    }

    @GetMapping("/my")
    @Operation(summary = "The caller's own order history")
    public ResponseEntity<PageResponse<OrderResponse>> myOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(PageResponse.of(
                orderService.search(caller.userId(), null, null, null, null,
                        PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "placedAt"))),
                order -> order));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a single order")
    public ResponseEntity<OrderResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getById(id, SecurityUtils.requireCurrentUser()));
    }

    @GetMapping("/reference/{reference}")
    @Operation(summary = "Fetch an order by its tracking reference")
    public ResponseEntity<OrderResponse> getByReference(@PathVariable String reference) {
        return ResponseEntity.ok(orderService.getByReference(reference, SecurityUtils.requireCurrentUser()));
    }

    @GetMapping("/reference/{reference}/track")
    @Operation(summary = "Live tracking view",
            description = "Slim payload for the customer tracking screen: current stage, estimated "
                    + "minutes remaining and the full timeline.")
    public ResponseEntity<OrderTrackingResponse> track(@PathVariable String reference) {
        return ResponseEntity.ok(orderService.track(reference, SecurityUtils.requireCurrentUser()));
    }

    @GetMapping("/kitchen/queue")
    @PreAuthorize("hasAnyRole('KITCHEN','STAFF','ADMIN')")
    @Operation(summary = "Kitchen display board",
            description = "Confirmed, preparing and ready tickets, oldest first.")
    public ResponseEntity<List<OrderResponse>> kitchenQueue() {
        return ResponseEntity.ok(orderService.kitchenQueue());
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('KITCHEN','STAFF','ADMIN')")
    @Operation(summary = "Advance an order through the kitchen workflow")
    public ResponseEntity<OrderResponse> updateStatus(@PathVariable Long id,
                                                      @Valid @RequestBody UpdateOrderStatusRequest request) {
        return ResponseEntity.ok(
                orderService.updateStatus(id, request, SecurityUtils.requireCurrentUser()));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel an order and return the reserved stock",
            description = "Customers may cancel their own order until the kitchen starts cooking; "
                    + "staff may cancel at any point before completion.")
    public ResponseEntity<OrderResponse> cancel(@PathVariable Long id,
                                                @Valid @RequestBody(required = false)
                                                CancelOrderRequest request) {
        return ResponseEntity.ok(orderService.cancel(id, request, SecurityUtils.requireCurrentUser()));
    }

    private static OrderChannel parseChannel(String value) {
        try {
            return OrderChannel.valueOf(value.trim().toUpperCase());
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException(
                    "Unknown channel '" + value + "'. Expected DINE_IN, TAKEAWAY or DELIVERY");
        }
    }
}
