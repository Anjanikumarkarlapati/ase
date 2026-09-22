package com.bistrobyte.menuservice;

import com.bistrobyte.common.exception.BusinessRuleException;
import com.bistrobyte.menuservice.domain.Category;
import com.bistrobyte.menuservice.domain.MenuItem;
import com.bistrobyte.menuservice.dto.AvailabilityRequest;
import com.bistrobyte.menuservice.dto.StockReleaseRequest;
import com.bistrobyte.menuservice.dto.StockReservationRequest;
import com.bistrobyte.menuservice.dto.StockReservationResponse;
import com.bistrobyte.menuservice.service.MenuItemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The stock reservation rules that keep sold-out dishes off customer orders. */
@SpringBootTest
@ActiveProfiles("test")
class MenuItemServiceTests extends MenuTestSupport {

    @Autowired
    private MenuItemService menuItemService;

    private Category mains;

    @BeforeEach
    void setUp() {
        clearMenu();
        mains = givenCategory("Mains");
    }

    @Test
    @DisplayName("A reservation prices the basket and decrements limited stock")
    void reservesAndPricesBasket() {
        MenuItem limited = givenItem(mains, "Butter Chicken", "14.50", 10);
        MenuItem unlimited = givenItem(mains, "Margherita Pizza", "11.90", null);

        StockReservationResponse response = menuItemService.reserve(new StockReservationRequest(
                "BB-TEST-0001",
                List.of(new StockReservationRequest.Line(limited.getId(), 2),
                        new StockReservationRequest.Line(unlimited.getId(), 1))));

        assertThat(response.lines()).hasSize(2);
        assertThat(response.subtotal()).isEqualByComparingTo(new BigDecimal("40.90"));
        assertThat(menuItemRepository.findById(limited.getId()).orElseThrow().getStockQuantity()).isEqualTo(8);
        // An unlimited dish is never given a stock count by a reservation.
        assertThat(menuItemRepository.findById(unlimited.getId()).orElseThrow().getStockQuantity()).isNull();
    }

    @Test
    @DisplayName("Duplicate lines for the same dish are merged before the stock check")
    void mergesDuplicateLines() {
        MenuItem item = givenItem(mains, "Lamb Seekh", "17.75", 3);

        assertThatThrownBy(() -> menuItemService.reserve(new StockReservationRequest(
                "BB-TEST-0002",
                List.of(new StockReservationRequest.Line(item.getId(), 2),
                        new StockReservationRequest.Line(item.getId(), 2)))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("only 3 portion(s) left");

        assertThat(menuItemRepository.findById(item.getId()).orElseThrow().getStockQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("An order for a sold-out dish is rejected and leaves all stock untouched")
    void rejectsSoldOutBasketAtomically() {
        MenuItem available = givenItem(mains, "Margherita Pizza", "11.90", 5);
        MenuItem soldOut = givenItem(mains, "Tiramisu", "6.40", 0);

        assertThatThrownBy(() -> menuItemService.reserve(new StockReservationRequest(
                "BB-TEST-0003",
                List.of(new StockReservationRequest.Line(available.getId(), 1),
                        new StockReservationRequest.Line(soldOut.getId(), 1)))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Tiramisu");

        // The whole reservation rolled back: the pizza was not consumed either.
        assertThat(menuItemRepository.findById(available.getId()).orElseThrow().getStockQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("A dish 86'd by staff cannot be ordered even with stock on the books")
    void rejectsManuallyDisabledDish() {
        MenuItem item = givenItem(mains, "Butter Chicken", "14.50", 10);
        menuItemService.setAvailability(item.getId(), new AvailabilityRequest(false, "Out of gravy"));

        assertThatThrownBy(() -> menuItemService.reserve(new StockReservationRequest(
                "BB-TEST-0004",
                List.of(new StockReservationRequest.Line(item.getId(), 1)))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("sold out");
    }

    @Test
    @DisplayName("The last portion auto-86s the dish so the next customer never sees it")
    void autoDisablesWhenStockHitsZero() {
        MenuItem item = givenItem(mains, "Lamb Seekh", "17.75", 1);

        menuItemService.reserve(new StockReservationRequest(
                "BB-TEST-0005", List.of(new StockReservationRequest.Line(item.getId(), 1))));

        MenuItem reloaded = menuItemRepository.findById(item.getId()).orElseThrow();
        assertThat(reloaded.getStockQuantity()).isZero();
        assertThat(reloaded.isAvailable()).isFalse();
        assertThat(reloaded.isOrderable()).isFalse();
    }

    @Test
    @DisplayName("Cancelling an order returns the portions and puts the dish back on the menu")
    void releasesStockOnCancellation() {
        MenuItem item = givenItem(mains, "Lamb Seekh", "17.75", 2);
        menuItemService.reserve(new StockReservationRequest(
                "BB-TEST-0006", List.of(new StockReservationRequest.Line(item.getId(), 2))));
        assertThat(menuItemRepository.findById(item.getId()).orElseThrow().isAvailable()).isFalse();

        menuItemService.release(new StockReleaseRequest(
                "BB-TEST-0006", List.of(new StockReleaseRequest.Line(item.getId(), 2))));

        MenuItem reloaded = menuItemRepository.findById(item.getId()).orElseThrow();
        assertThat(reloaded.getStockQuantity()).isEqualTo(2);
        assertThat(reloaded.isOrderable()).isTrue();
    }

    @Test
    @DisplayName("A retired dish cannot be reserved")
    void rejectsRetiredDish() {
        MenuItem item = givenItem(mains, "Old Special", "9.00", 5);
        menuItemService.retire(item.getId());

        assertThatThrownBy(() -> menuItemService.reserve(new StockReservationRequest(
                "BB-TEST-0007",
                List.of(new StockReservationRequest.Line(item.getId(), 1)))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no longer on the menu");
    }

    @Test
    @DisplayName("A reservation for a dish that does not exist is rejected")
    void rejectsUnknownDish() {
        assertThatThrownBy(() -> menuItemService.reserve(new StockReservationRequest(
                "BB-TEST-0008",
                List.of(new StockReservationRequest.Line(9_999L, 1)))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("does not exist");
    }
}
