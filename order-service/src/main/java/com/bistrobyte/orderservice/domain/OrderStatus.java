package com.bistrobyte.orderservice.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle of an order ticket. The allowed transitions are encoded here so the kitchen
 * display, the staff console and the API can never disagree about what comes next.
 */
public enum OrderStatus {

    /** Accepted by the platform, stock reserved, awaiting kitchen acknowledgement. */
    PENDING,

    /** Acknowledged by staff; the ticket is queued for the kitchen. */
    CONFIRMED,

    /** On the pass being cooked. */
    PREPARING,

    /** Ready for pickup, service or dispatch. */
    READY,

    /** Delivery orders only: with the rider. */
    OUT_FOR_DELIVERY,

    /** Served, collected or delivered. Terminal. */
    COMPLETED,

    /** Cancelled before it was served; reserved stock is returned. Terminal. */
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }

    /** Statuses the kitchen display board cares about. */
    public static Set<OrderStatus> kitchenQueue() {
        return EnumSet.of(CONFIRMED, PREPARING, READY);
    }

    /** Transitions permitted from this status for the given channel. */
    public Set<OrderStatus> allowedNext(OrderChannel channel) {
        return switch (this) {
            case PENDING -> EnumSet.of(CONFIRMED, CANCELLED);
            case CONFIRMED -> EnumSet.of(PREPARING, CANCELLED);
            case PREPARING -> EnumSet.of(READY, CANCELLED);
            case READY -> channel == OrderChannel.DELIVERY
                    ? EnumSet.of(OUT_FOR_DELIVERY, COMPLETED, CANCELLED)
                    : EnumSet.of(COMPLETED, CANCELLED);
            case OUT_FOR_DELIVERY -> EnumSet.of(COMPLETED, CANCELLED);
            case COMPLETED, CANCELLED -> EnumSet.noneOf(OrderStatus.class);
        };
    }

    public boolean canTransitionTo(OrderStatus target, OrderChannel channel) {
        return allowedNext(channel).contains(target);
    }

    public static OrderStatus from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Order status must not be blank");
        }
        return OrderStatus.valueOf(value.trim().toUpperCase());
    }
}
