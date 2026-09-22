package com.bistrobyte.orderservice.dto;

import com.bistrobyte.orderservice.domain.CustomerOrder;
import com.bistrobyte.orderservice.domain.OrderChannel;
import com.bistrobyte.orderservice.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Full ticket view used by the customer app, the staff console and the kitchen display. */
public record OrderResponse(Long id,
                            String orderReference,
                            Long customerId,
                            String customerUsername,
                            OrderChannel channel,
                            OrderStatus status,
                            Set<OrderStatus> allowedNextStatuses,
                            String tableNumber,
                            String deliveryAddress,
                            String contactPhone,
                            String customerNote,
                            List<OrderItemResponse> items,
                            BigDecimal subtotal,
                            BigDecimal taxAmount,
                            BigDecimal deliveryFee,
                            BigDecimal totalAmount,
                            Instant estimatedReadyAt,
                            Instant placedAt,
                            Instant updatedAt,
                            Instant completedAt,
                            String cancellationReason,
                            List<OrderStatusEventResponse> statusHistory) {

    public static OrderResponse from(CustomerOrder order) {
        return new OrderResponse(
                order.getId(),
                order.getOrderReference(),
                order.getCustomerId(),
                order.getCustomerUsername(),
                order.getChannel(),
                order.getStatus(),
                order.getStatus().allowedNext(order.getChannel()),
                order.getTableNumber(),
                order.getDeliveryAddress(),
                order.getContactPhone(),
                order.getCustomerNote(),
                order.getItems().stream().map(OrderItemResponse::from).toList(),
                order.getSubtotal(),
                order.getTaxAmount(),
                order.getDeliveryFee(),
                order.getTotalAmount(),
                order.getEstimatedReadyAt(),
                order.getPlacedAt(),
                order.getUpdatedAt(),
                order.getCompletedAt(),
                order.getCancellationReason(),
                order.getStatusHistory().stream().map(OrderStatusEventResponse::from).toList());
    }
}
