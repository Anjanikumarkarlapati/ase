package com.bistrobyte.orderservice.client.dto;

import java.math.BigDecimal;
import java.util.List;

public record StockReservationResult(String orderReference,
                                     List<ReservedLine> lines,
                                     BigDecimal subtotal,
                                     int estimatedPreparationMinutes) {

    public record ReservedLine(Long menuItemId,
                               String name,
                               BigDecimal unitPrice,
                               int quantity,
                               BigDecimal lineTotal,
                               int preparationMinutes) {
    }
}
