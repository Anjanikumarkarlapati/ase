package com.bistrobyte.orderservice;

import com.bistrobyte.orderservice.domain.OrderChannel;
import com.bistrobyte.orderservice.domain.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The kitchen workflow state machine, checked without a Spring context. */
class OrderStatusTests {

    @Test
    @DisplayName("A dine-in ticket walks PENDING to COMPLETED one stage at a time")
    void dineInHappyPath() {
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.CONFIRMED, OrderChannel.DINE_IN)).isTrue();
        assertThat(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.PREPARING, OrderChannel.DINE_IN)).isTrue();
        assertThat(OrderStatus.PREPARING.canTransitionTo(OrderStatus.READY, OrderChannel.DINE_IN)).isTrue();
        assertThat(OrderStatus.READY.canTransitionTo(OrderStatus.COMPLETED, OrderChannel.DINE_IN)).isTrue();
    }

    @Test
    @DisplayName("Stages cannot be skipped")
    void rejectsSkippedStages() {
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.READY, OrderChannel.DINE_IN)).isFalse();
        assertThat(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.COMPLETED, OrderChannel.TAKEAWAY)).isFalse();
    }

    @Test
    @DisplayName("Only delivery tickets can go out for delivery")
    void outForDeliveryIsDeliveryOnly() {
        assertThat(OrderStatus.READY.canTransitionTo(OrderStatus.OUT_FOR_DELIVERY, OrderChannel.DELIVERY)).isTrue();
        assertThat(OrderStatus.READY.canTransitionTo(OrderStatus.OUT_FOR_DELIVERY, OrderChannel.DINE_IN)).isFalse();
        assertThat(OrderStatus.READY.canTransitionTo(OrderStatus.OUT_FOR_DELIVERY, OrderChannel.TAKEAWAY)).isFalse();
    }

    @Test
    @DisplayName("Terminal statuses accept nothing further")
    void terminalStatusesAreFinal() {
        assertThat(OrderStatus.COMPLETED.isTerminal()).isTrue();
        assertThat(OrderStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(OrderStatus.COMPLETED.allowedNext(OrderChannel.DELIVERY)).isEmpty();
        assertThat(OrderStatus.CANCELLED.allowedNext(OrderChannel.DINE_IN)).isEmpty();
    }

    @Test
    @DisplayName("The kitchen board shows confirmed, preparing and ready tickets")
    void kitchenQueueContents() {
        assertThat(OrderStatus.kitchenQueue())
                .containsExactlyInAnyOrder(OrderStatus.CONFIRMED, OrderStatus.PREPARING, OrderStatus.READY);
    }

    @Test
    @DisplayName("Status parsing is case-insensitive and rejects nonsense")
    void parsesStatus() {
        assertThat(OrderStatus.from("preparing")).isEqualTo(OrderStatus.PREPARING);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> OrderStatus.from("ON_FIRE"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
