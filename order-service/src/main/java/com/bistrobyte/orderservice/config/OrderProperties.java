package com.bistrobyte.orderservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/** Commercial rules that vary per market, kept out of the code. */
@ConfigurationProperties(prefix = "bistrobyte.orders")
public class OrderProperties {

    /** Applied to the subtotal, e.g. 0.05 for 5%. */
    private BigDecimal taxRate = new BigDecimal("0.05");

    /** Flat fee added to DELIVERY orders. */
    private BigDecimal deliveryFee = new BigDecimal("2.50");

    /** Padding added to the slowest dish when promising a ready time. */
    private int deliveryPreparationBufferMinutes = 20;

    /** Padding added for dine-in and takeaway tickets. */
    private int inHousePreparationBufferMinutes = 5;

    public BigDecimal getTaxRate() {
        return taxRate;
    }

    public void setTaxRate(BigDecimal taxRate) {
        this.taxRate = taxRate;
    }

    public BigDecimal getDeliveryFee() {
        return deliveryFee;
    }

    public void setDeliveryFee(BigDecimal deliveryFee) {
        this.deliveryFee = deliveryFee;
    }

    public int getDeliveryPreparationBufferMinutes() {
        return deliveryPreparationBufferMinutes;
    }

    public void setDeliveryPreparationBufferMinutes(int deliveryPreparationBufferMinutes) {
        this.deliveryPreparationBufferMinutes = deliveryPreparationBufferMinutes;
    }

    public int getInHousePreparationBufferMinutes() {
        return inHousePreparationBufferMinutes;
    }

    public void setInHousePreparationBufferMinutes(int inHousePreparationBufferMinutes) {
        this.inHousePreparationBufferMinutes = inHousePreparationBufferMinutes;
    }
}
