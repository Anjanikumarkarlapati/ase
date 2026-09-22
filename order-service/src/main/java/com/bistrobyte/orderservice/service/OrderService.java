package com.bistrobyte.orderservice.service;

import com.bistrobyte.common.exception.BusinessRuleException;
import com.bistrobyte.common.exception.ForbiddenOperationException;
import com.bistrobyte.common.exception.ResourceNotFoundException;
import com.bistrobyte.common.security.AuthenticatedUser;
import com.bistrobyte.common.security.Role;
import com.bistrobyte.orderservice.client.MenuServiceClient;
import com.bistrobyte.orderservice.client.dto.StockReleaseCommand;
import com.bistrobyte.orderservice.client.dto.StockReservationCommand;
import com.bistrobyte.orderservice.client.dto.StockReservationResult;
import com.bistrobyte.orderservice.config.OrderProperties;
import com.bistrobyte.orderservice.domain.CustomerOrder;
import com.bistrobyte.orderservice.domain.OrderChannel;
import com.bistrobyte.orderservice.domain.OrderItem;
import com.bistrobyte.orderservice.domain.OrderStatus;
import com.bistrobyte.orderservice.domain.OrderStatusEvent;
import com.bistrobyte.orderservice.dto.CancelOrderRequest;
import com.bistrobyte.orderservice.dto.OrderResponse;
import com.bistrobyte.orderservice.dto.OrderTrackingResponse;
import com.bistrobyte.orderservice.dto.PlaceOrderRequest;
import com.bistrobyte.orderservice.dto.UpdateOrderStatusRequest;
import com.bistrobyte.orderservice.repository.CustomerOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Order placement, kitchen execution and tracking.
 *
 * <p>Placement is a two-step saga: stock is reserved in the menu service first, and only
 * then is the ticket written. If the write fails the reservation is compensated, so the
 * kitchen can never hold stock for an order that does not exist.</p>
 */
@Service
@Transactional(readOnly = true)
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final CustomerOrderRepository repository;
    private final MenuServiceClient menuServiceClient;
    private final OrderReferenceGenerator referenceGenerator;
    private final OrderProperties properties;

    public OrderService(CustomerOrderRepository repository,
                        MenuServiceClient menuServiceClient,
                        OrderReferenceGenerator referenceGenerator,
                        OrderProperties properties) {
        this.repository = repository;
        this.menuServiceClient = menuServiceClient;
        this.referenceGenerator = referenceGenerator;
        this.properties = properties;
    }

    @Transactional
    public OrderResponse place(PlaceOrderRequest request, AuthenticatedUser caller) {
        OrderChannel channel = parseChannel(request.channel());
        validateChannelRequirements(channel, request);

        String reference = referenceGenerator.next();
        StockReservationResult reservation = menuServiceClient.reserve(new StockReservationCommand(
                reference,
                request.items().stream()
                        .map(line -> new StockReservationCommand.Line(line.menuItemId(), line.quantity()))
                        .toList()));

        try {
            CustomerOrder order = buildOrder(request, channel, caller, reference, reservation);
            CustomerOrder saved = repository.save(order);
            log.info("Placed order {} ({} lines, total {}) for customer {}",
                    saved.getOrderReference(), saved.getItems().size(), saved.getTotalAmount(),
                    saved.getCustomerUsername());
            return OrderResponse.from(saved);
        } catch (RuntimeException ex) {
            // Compensate: the stock is already held by the menu service but no ticket exists.
            log.error("Order {} failed after stock was reserved; releasing the reservation", reference, ex);
            safeRelease(reference, request.items().stream()
                    .map(line -> new StockReleaseCommand.Line(line.menuItemId(), line.quantity()))
                    .toList());
            throw ex;
        }
    }

    private CustomerOrder buildOrder(PlaceOrderRequest request,
                                     OrderChannel channel,
                                     AuthenticatedUser caller,
                                     String reference,
                                     StockReservationResult reservation) {
        CustomerOrder order = new CustomerOrder();
        order.setOrderReference(reference);
        order.setCustomerId(caller.userId());
        order.setCustomerUsername(caller.username());
        order.setChannel(channel);
        order.setStatus(OrderStatus.PENDING);
        order.setTableNumber(trimToNull(request.tableNumber()));
        order.setDeliveryAddress(trimToNull(request.deliveryAddress()));
        order.setContactPhone(trimToNull(request.contactPhone()));
        order.setCustomerNote(trimToNull(request.customerNote()));
        order.setDeliveryFee(channel == OrderChannel.DELIVERY
                ? properties.getDeliveryFee()
                : BigDecimal.ZERO);

        // Special instructions are keyed per dish; duplicate lines were merged upstream.
        Map<Long, String> instructions = new HashMap<>();
        for (PlaceOrderRequest.Line line : request.items()) {
            if (line.specialInstructions() != null && !line.specialInstructions().isBlank()) {
                instructions.merge(line.menuItemId(), line.specialInstructions().trim(),
                        (existing, added) -> existing + "; " + added);
            }
        }

        for (StockReservationResult.ReservedLine reserved : reservation.lines()) {
            OrderItem item = new OrderItem();
            item.setMenuItemId(reserved.menuItemId());
            item.setItemName(reserved.name());
            item.setUnitPrice(reserved.unitPrice());
            item.setQuantity(reserved.quantity());
            item.setLineTotal(reserved.lineTotal());
            item.setSpecialInstructions(instructions.get(reserved.menuItemId()));
            order.addItem(item);
        }

        order.recalculateTotals(properties.getTaxRate());
        order.setEstimatedReadyAt(estimateReadyAt(channel, reservation.estimatedPreparationMinutes()));
        order.addStatusEvent(OrderStatusEvent.of(order, OrderStatus.PENDING, caller.username(),
                "Order received through the " + channel.name().toLowerCase().replace('_', ' ') + " channel"));
        return order;
    }

    private Instant estimateReadyAt(OrderChannel channel, int slowestDishMinutes) {
        int buffer = channel == OrderChannel.DELIVERY
                ? properties.getDeliveryPreparationBufferMinutes()
                : properties.getInHousePreparationBufferMinutes();
        return Instant.now().plus(slowestDishMinutes + (long) buffer, ChronoUnit.MINUTES);
    }

    public Page<OrderResponse> search(Long customerId,
                                      OrderStatus status,
                                      OrderChannel channel,
                                      Instant from,
                                      Instant to,
                                      Pageable pageable) {
        return repository.search(customerId, status, channel, from, to, pageable).map(OrderResponse::from);
    }

    public OrderResponse getById(Long id, AuthenticatedUser caller) {
        CustomerOrder order = repository.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
        assertVisible(order, caller);
        return OrderResponse.from(order);
    }

    public OrderResponse getByReference(String reference, AuthenticatedUser caller) {
        CustomerOrder order = findByReferenceOrThrow(reference);
        assertVisible(order, caller);
        return OrderResponse.from(order);
    }

    public OrderTrackingResponse track(String reference, AuthenticatedUser caller) {
        CustomerOrder order = findByReferenceOrThrow(reference);
        assertVisible(order, caller);
        return OrderTrackingResponse.from(order);
    }

    /** Live kitchen board: confirmed, preparing and ready tickets, oldest first. */
    public List<OrderResponse> kitchenQueue() {
        return repository.findByStatusInOrderByPlacedAtAsc(OrderStatus.kitchenQueue())
                .stream()
                .map(OrderResponse::from)
                .toList();
    }

    /** Advances a ticket. Only transitions legal for the channel are accepted. */
    @Transactional
    public OrderResponse updateStatus(Long id, UpdateOrderStatusRequest request, AuthenticatedUser caller) {
        CustomerOrder order = repository.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
        OrderStatus target = OrderStatus.from(request.status());

        if (target == OrderStatus.CANCELLED) {
            throw new BusinessRuleException(
                    "Use POST /api/v1/orders/{id}/cancel to cancel an order so stock is returned");
        }
        if (order.getStatus() == target) {
            throw new BusinessRuleException("Order " + order.getOrderReference()
                    + " is already " + target);
        }
        if (!order.getStatus().canTransitionTo(target, order.getChannel())) {
            throw new BusinessRuleException("Cannot move order " + order.getOrderReference() + " from "
                    + order.getStatus() + " to " + target + ". Allowed: "
                    + order.getStatus().allowedNext(order.getChannel()));
        }

        order.setStatus(target);
        if (target == OrderStatus.COMPLETED) {
            order.setCompletedAt(Instant.now());
        }
        order.addStatusEvent(OrderStatusEvent.of(order, target, caller.username(), request.note()));
        CustomerOrder saved = repository.save(order);
        log.info("Order {} moved to {} by {}", saved.getOrderReference(), target, caller.username());
        return OrderResponse.from(saved);
    }

    /**
     * Cancels a ticket and hands the reserved portions back to the menu service. Customers
     * may only cancel their own order while the kitchen has not started cooking.
     */
    @Transactional
    public OrderResponse cancel(Long id, CancelOrderRequest request, AuthenticatedUser caller) {
        CustomerOrder order = repository.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
        boolean staffLevel = isStaffLevel(caller);

        if (!staffLevel && !order.getCustomerId().equals(caller.userId())) {
            throw new ForbiddenOperationException("You may only cancel your own orders");
        }
        if (order.getStatus().isTerminal()) {
            throw new BusinessRuleException(
                    "Order " + order.getOrderReference() + " is already " + order.getStatus());
        }
        if (!staffLevel && order.getStatus() != OrderStatus.PENDING
                && order.getStatus() != OrderStatus.CONFIRMED) {
            throw new BusinessRuleException("Order " + order.getOrderReference()
                    + " is already being prepared. Please call the restaurant to cancel it.");
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setCancellationReason(trimToNull(request == null ? null : request.reason()));
        order.addStatusEvent(OrderStatusEvent.of(order, OrderStatus.CANCELLED, caller.username(),
                order.getCancellationReason()));

        if (!order.isStockReleased()) {
            safeRelease(order.getOrderReference(), order.getItems().stream()
                    .map(item -> new StockReleaseCommand.Line(item.getMenuItemId(), item.getQuantity()))
                    .toList());
            order.setStockReleased(true);
        }

        CustomerOrder saved = repository.save(order);
        log.info("Order {} cancelled by {}", saved.getOrderReference(), caller.username());
        return OrderResponse.from(saved);
    }

    /**
     * Releasing stock must never turn a successful cancellation into a failure: the ticket
     * is already cancelled, so a menu-service outage is logged for reconciliation instead.
     */
    private void safeRelease(String reference, List<StockReleaseCommand.Line> lines) {
        if (lines.isEmpty()) {
            return;
        }
        try {
            menuServiceClient.release(new StockReleaseCommand(reference, lines));
        } catch (RuntimeException ex) {
            log.error("Could not release stock for order {}; manual reconciliation required",
                    reference, ex);
        }
    }

    private CustomerOrder findByReferenceOrThrow(String reference) {
        return repository.findByOrderReference(reference.trim().toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No order found for reference " + reference));
    }

    /** Customers see only their own tickets; staff, kitchen and admin see everything. */
    private void assertVisible(CustomerOrder order, AuthenticatedUser caller) {
        if (isStaffLevel(caller)) {
            return;
        }
        if (!order.getCustomerId().equals(caller.userId())) {
            throw new ForbiddenOperationException("This order belongs to another customer");
        }
    }

    private static boolean isStaffLevel(AuthenticatedUser caller) {
        return caller.role() == Role.STAFF || caller.role() == Role.KITCHEN || caller.role() == Role.ADMIN;
    }

    private static OrderChannel parseChannel(String value) {
        try {
            return OrderChannel.valueOf(value.trim().toUpperCase());
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException(
                    "Unknown channel '" + value + "'. Expected DINE_IN, TAKEAWAY or DELIVERY");
        }
    }

    private static void validateChannelRequirements(OrderChannel channel, PlaceOrderRequest request) {
        switch (channel) {
            case DINE_IN -> {
                if (isBlank(request.tableNumber())) {
                    throw new BusinessRuleException("A table number is required for dine-in orders");
                }
            }
            case TAKEAWAY -> {
                if (isBlank(request.contactPhone())) {
                    throw new BusinessRuleException(
                            "A contact phone number is required for takeaway orders");
                }
            }
            case DELIVERY -> {
                if (isBlank(request.deliveryAddress())) {
                    throw new BusinessRuleException("A delivery address is required for delivery orders");
                }
                if (isBlank(request.contactPhone())) {
                    throw new BusinessRuleException(
                            "A contact phone number is required for delivery orders");
                }
            }
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }
}
