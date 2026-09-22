package com.bistrobyte.menuservice.dto;

import java.math.BigDecimal;
import java.util.List;

/** Priced, reserved basket returned to the order service. */
public record StockReservationResponse(String orderReference,
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
