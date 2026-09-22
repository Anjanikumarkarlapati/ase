package com.bistrobyte.orderservice.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/** A customer ticket across any of the three channels. */
@Entity
@Table(name = "customer_orders",
        uniqueConstraints = @UniqueConstraint(name = "uk_order_reference", columnNames = "order_reference"))
public class CustomerOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Human-friendly tracking code handed to the customer, e.g. {@code BB-20260921-7F3A}. */
    @Column(name = "order_reference", nullable = false, length = 32)
    private String orderReference;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "customer_username", nullable = false, length = 60)
    private String customerUsername;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OrderChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private OrderStatus status = OrderStatus.PENDING;

    /** Dine-in only. */
    @Column(name = "table_number", length = 10)
    private String tableNumber;

    /** Delivery only. */
    @Column(name = "delivery_address", length = 300)
    private String deliveryAddress;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    @Column(name = "customer_note", length = 500)
    private String customerNote;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "delivery_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal deliveryFee = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    /** Promise shown to the customer, derived from the slowest dish on the ticket. */
    @Column(name = "estimated_ready_at")
    private Instant estimatedReadyAt;

    @Column(name = "cancellation_reason", length = 255)
    private String cancellationReason;

    @Column(name = "placed_at", nullable = false, updatable = false)
    private Instant placedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    /** True once reserved stock has been handed back, so a cancel can never double-release. */
    @Column(name = "stock_released", nullable = false)
    private boolean stockReleased = false;

    @Version
    @Column(nullable = false)
    private long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    private Set<OrderItem> items = new LinkedHashSet<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("changedAt ASC, id ASC")
    private Set<OrderStatusEvent> statusHistory = new LinkedHashSet<>();

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.placedAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void addItem(OrderItem item) {
        item.setOrder(this);
        this.items.add(item);
    }

    public void addStatusEvent(OrderStatusEvent event) {
        event.setOrder(this);
        this.statusHistory.add(event);
    }

    /** Recomputes the money columns from the current lines. */
    public void recalculateTotals(BigDecimal taxRate) {
        this.subtotal = items.stream()
                .map(OrderItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, java.math.RoundingMode.HALF_UP);
        this.taxAmount = this.subtotal.multiply(taxRate).setScale(2, java.math.RoundingMode.HALF_UP);
        this.totalAmount = this.subtotal.add(this.taxAmount).add(this.deliveryFee)
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    public Long getId() {
        return id;
    }

    public String getOrderReference() {
        return orderReference;
    }

    public void setOrderReference(String orderReference) {
        this.orderReference = orderReference;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getCustomerUsername() {
        return customerUsername;
    }

    public void setCustomerUsername(String customerUsername) {
        this.customerUsername = customerUsername;
    }

    public OrderChannel getChannel() {
        return channel;
    }

    public void setChannel(OrderChannel channel) {
        this.channel = channel;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public String getTableNumber() {
        return tableNumber;
    }

    public void setTableNumber(String tableNumber) {
        this.tableNumber = tableNumber;
    }

    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    public void setDeliveryAddress(String deliveryAddress) {
        this.deliveryAddress = deliveryAddress;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public String getCustomerNote() {
        return customerNote;
    }

    public void setCustomerNote(String customerNote) {
        this.customerNote = customerNote;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public BigDecimal getDeliveryFee() {
        return deliveryFee;
    }

    public void setDeliveryFee(BigDecimal deliveryFee) {
        this.deliveryFee = deliveryFee;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public Instant getEstimatedReadyAt() {
        return estimatedReadyAt;
    }

    public void setEstimatedReadyAt(Instant estimatedReadyAt) {
        this.estimatedReadyAt = estimatedReadyAt;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public void setCancellationReason(String cancellationReason) {
        this.cancellationReason = cancellationReason;
    }

    public Instant getPlacedAt() {
        return placedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public boolean isStockReleased() {
        return stockReleased;
    }

    public void setStockReleased(boolean stockReleased) {
        this.stockReleased = stockReleased;
    }

    public Set<OrderItem> getItems() {
        return items;
    }

    public Set<OrderStatusEvent> getStatusHistory() {
        return statusHistory;
    }
}
