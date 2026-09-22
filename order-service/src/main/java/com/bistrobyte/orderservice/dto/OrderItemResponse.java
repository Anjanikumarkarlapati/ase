package com.bistrobyte.orderservice.dto;

import com.bistrobyte.orderservice.domain.OrderItem;

import java.math.BigDecimal;

public record OrderItemResponse(Long id,
                                Long menuItemId,
                                String itemName,
                                BigDecimal unitPrice,
                                int quantity,
                                BigDecimal lineTotal,
                                String specialInstructions) {

    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(item.getId(), item.getMenuItemId(), item.getItemName(),
                item.getUnitPrice(), item.getQuantity(), item.getLineTotal(), item.getSpecialInstructions());
    }
}
