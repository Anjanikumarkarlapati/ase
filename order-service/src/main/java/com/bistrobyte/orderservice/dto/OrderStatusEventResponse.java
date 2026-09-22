package com.bistrobyte.orderservice.dto;

import com.bistrobyte.orderservice.domain.OrderStatus;
import com.bistrobyte.orderservice.domain.OrderStatusEvent;

import java.time.Instant;

public record OrderStatusEventResponse(OrderStatus status,
                                       Instant changedAt,
                                       String changedBy,
                                       String note) {

    public static OrderStatusEventResponse from(OrderStatusEvent event) {
        return new OrderStatusEventResponse(event.getStatus(), event.getChangedAt(),
                event.getChangedBy(), event.getNote());
    }
}
