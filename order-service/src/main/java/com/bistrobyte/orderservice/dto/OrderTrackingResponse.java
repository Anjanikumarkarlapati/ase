package com.bistrobyte.orderservice.dto;

import com.bistrobyte.orderservice.domain.CustomerOrder;
import com.bistrobyte.orderservice.domain.OrderChannel;
import com.bistrobyte.orderservice.domain.OrderStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Slim payload the customer tracking screen polls. */
public record OrderTrackingResponse(String orderReference,
                                    OrderStatus status,
                                    OrderChannel channel,
                                    String statusLabel,
                                    Instant estimatedReadyAt,
                                    Long minutesRemaining,
                                    boolean terminal,
                                    List<OrderStatusEventResponse> timeline) {

    public static OrderTrackingResponse from(CustomerOrder order) {
        Long minutesRemaining = null;
        if (!order.getStatus().isTerminal() && order.getEstimatedReadyAt() != null) {
            long minutes = Duration.between(Instant.now(), order.getEstimatedReadyAt()).toMinutes();
            minutesRemaining = Math.max(minutes, 0);
        }
        return new OrderTrackingResponse(
                order.getOrderReference(),
                order.getStatus(),
                order.getChannel(),
                label(order.getStatus(), order.getChannel()),
                order.getEstimatedReadyAt(),
                minutesRemaining,
                order.getStatus().isTerminal(),
                order.getStatusHistory().stream().map(OrderStatusEventResponse::from).toList());
    }

    private static String label(OrderStatus status, OrderChannel channel) {
        return switch (status) {
            case PENDING -> "Order received, waiting for the restaurant to confirm";
            case CONFIRMED -> "Confirmed and queued for the kitchen";
            case PREPARING -> "The kitchen is preparing your order";
            case READY -> switch (channel) {
                case DINE_IN -> "Ready, being served to your table";
                case TAKEAWAY -> "Ready for collection at the counter";
                case DELIVERY -> "Ready and waiting for a rider";
            };
            case OUT_FOR_DELIVERY -> "On the way to you";
            case COMPLETED -> "Completed. Enjoy!";
            case CANCELLED -> "Cancelled";
        };
    }
}
