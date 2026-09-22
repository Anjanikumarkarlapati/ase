package com.bistrobyte.orderservice;

import com.bistrobyte.common.security.JwtService;
import com.bistrobyte.common.security.Role;
import com.bistrobyte.orderservice.client.MenuServiceClient;
import com.bistrobyte.orderservice.client.dto.StockReleaseCommand;
import com.bistrobyte.orderservice.client.dto.StockReservationCommand;
import com.bistrobyte.orderservice.client.dto.StockReservationResult;
import com.bistrobyte.orderservice.repository.CustomerOrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Order placement, kitchen execution and tracking over HTTP. The menu service is mocked so
 * the tests exercise this service's own rules, including the compensating stock release.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderApiTests {

    private static final long CUSTOMER_ID = 42L;
    private static final long OTHER_CUSTOMER_ID = 99L;
    private static final long PIZZA_ID = 7L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private CustomerOrderRepository repository;

    @MockBean
    private MenuServiceClient menuServiceClient;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        given(menuServiceClient.reserve(any())).willAnswer(invocation -> {
            StockReservationCommand command = invocation.getArgument(0);
            return new StockReservationResult(
                    command.orderReference(),
                    List.of(new StockReservationResult.ReservedLine(
                            PIZZA_ID, "Margherita Pizza", new BigDecimal("11.90"), 2,
                            new BigDecimal("23.80"), 18)),
                    new BigDecimal("23.80"),
                    18);
        });
    }

    @Test
    @DisplayName("A dine-in order is placed, priced with tax and given a tracking reference")
    void placesDineInOrder() throws Exception {
        String response = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", bearer(CUSTOMER_ID, "casey", Role.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "channel", "DINE_IN",
                                "tableNumber", "12",
                                "items", List.of(Map.of("menuItemId", PIZZA_ID, "quantity", 2))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.channel").value("DINE_IN"))
                .andExpect(jsonPath("$.subtotal").value(23.80))
                // 5% tax, no delivery fee for dine-in.
                .andExpect(jsonPath("$.taxAmount").value(1.19))
                .andExpect(jsonPath("$.totalAmount").value(24.99))
                .andExpect(jsonPath("$.items[0].itemName").value("Margherita Pizza"))
                .andExpect(jsonPath("$.statusHistory[0].status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();

        String reference = objectMapper.readTree(response).get("orderReference").asText();
        assertThat(reference).startsWith("BB-");
        assertThat(repository.existsByOrderReference(reference)).isTrue();
    }

    @Test
    @DisplayName("A delivery order carries the delivery fee in its total")
    void addsDeliveryFee() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", bearer(CUSTOMER_ID, "casey", Role.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "channel", "DELIVERY",
                                "deliveryAddress", "14 Harbour Road, Apt 3",
                                "contactPhone", "+44 7700 900123",
                                "items", List.of(Map.of("menuItemId", PIZZA_ID, "quantity", 2))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deliveryFee").value(2.50))
                .andExpect(jsonPath("$.totalAmount").value(27.49));
    }

    @Test
    @DisplayName("Channel requirements are enforced before any stock is reserved")
    void enforcesChannelRequirements() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", bearer(CUSTOMER_ID, "casey", Role.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "channel", "DINE_IN",
                                "items", List.of(Map.of("menuItemId", PIZZA_ID, "quantity", 1))))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("table number")));

        verify(menuServiceClient, never()).reserve(any());
    }

    @Test
    @DisplayName("A sold-out dish reported by the menu service surfaces as 409 with the real reason")
    void propagatesSoldOutFromMenuService() throws Exception {
        org.mockito.BDDMockito.willThrow(new com.bistrobyte.common.exception.BusinessRuleException(
                        "Order cannot be placed: 'Margherita Pizza' is sold out for today"))
                .given(menuServiceClient).reserve(any());

        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", bearer(CUSTOMER_ID, "casey", Role.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "channel", "TAKEAWAY",
                                "contactPhone", "+44 7700 900123",
                                "items", List.of(Map.of("menuItemId", PIZZA_ID, "quantity", 2))))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("sold out")));

        assertThat(repository.count()).isZero();
    }

    @Test
    @DisplayName("An empty basket is rejected by validation")
    void rejectsEmptyBasket() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", bearer(CUSTOMER_ID, "casey", Role.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "channel", "DINE_IN",
                                "tableNumber", "12",
                                "items", List.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations").isArray());
    }

    @Test
    @DisplayName("The kitchen advances a ticket stage by stage and cannot skip ahead")
    void kitchenWorkflow() throws Exception {
        Long orderId = placeDineInOrder();

        advance(orderId, "CONFIRMED").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        advance(orderId, "READY").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Cannot move order")));
        advance(orderId, "PREPARING").andExpect(status().isOk());
        advance(orderId, "READY").andExpect(status().isOk());
        advance(orderId, "COMPLETED").andExpect(status().isOk())
                .andExpect(jsonPath("$.completedAt").isNotEmpty())
                .andExpect(jsonPath("$.statusHistory.length()").value(5));
    }

    @Test
    @DisplayName("A customer cannot drive the kitchen workflow")
    void customerCannotAdvanceStatus() throws Exception {
        Long orderId = placeDineInOrder();

        mockMvc.perform(patch("/api/v1/orders/" + orderId + "/status")
                        .header("Authorization", bearer(CUSTOMER_ID, "casey", Role.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "CONFIRMED"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Cancelling releases the reserved stock exactly once")
    void cancellationReleasesStock() throws Exception {
        Long orderId = placeDineInOrder();

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancel")
                        .header("Authorization", bearer(CUSTOMER_ID, "casey", Role.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "Changed my mind"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("Changed my mind"));

        ArgumentCaptor<StockReleaseCommand> captor = ArgumentCaptor.forClass(StockReleaseCommand.class);
        verify(menuServiceClient).release(captor.capture());
        assertThat(captor.getValue().lines()).hasSize(1);
        assertThat(captor.getValue().lines().get(0).quantity()).isEqualTo(2);

        // A second cancellation is refused, so stock is never released twice.
        mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancel")
                        .header("Authorization", bearer(CUSTOMER_ID, "casey", Role.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("A customer cannot cancel once the kitchen has started cooking")
    void customerCannotCancelWhilePreparing() throws Exception {
        Long orderId = placeDineInOrder();
        advance(orderId, "CONFIRMED");
        advance(orderId, "PREPARING");

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancel")
                        .header("Authorization", bearer(CUSTOMER_ID, "casey", Role.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("already being prepared")));

        // Staff may still cancel it.
        mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancel")
                        .header("Authorization", bearer(1L, "sam", Role.STAFF))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "Kitchen equipment failure"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("One customer cannot read another customer's order")
    void ordersAreScopedToTheirCustomer() throws Exception {
        Long orderId = placeDineInOrder();

        mockMvc.perform(get("/api/v1/orders/" + orderId)
                        .header("Authorization", bearer(OTHER_CUSTOMER_ID, "nosy", Role.CUSTOMER)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/orders/" + orderId)
                        .header("Authorization", bearer(1L, "sam", Role.STAFF)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Listing orders as a customer returns only that customer's tickets")
    void listingIsScopedForCustomers() throws Exception {
        placeDineInOrder();

        mockMvc.perform(get("/api/v1/orders")
                        .header("Authorization", bearer(OTHER_CUSTOMER_ID, "nosy", Role.CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/api/v1/orders/my")
                        .header("Authorization", bearer(CUSTOMER_ID, "casey", Role.CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("Tracking exposes the live stage and the timeline")
    void tracksOrderByReference() throws Exception {
        String reference = placeDineInOrderAndReturnReference();
        Long orderId = repository.findByOrderReference(reference).orElseThrow().getId();
        advance(orderId, "CONFIRMED");
        advance(orderId, "PREPARING");

        mockMvc.perform(get("/api/v1/orders/reference/" + reference + "/track")
                        .header("Authorization", bearer(CUSTOMER_ID, "casey", Role.CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PREPARING"))
                .andExpect(jsonPath("$.statusLabel").value(
                        org.hamcrest.Matchers.containsString("preparing")))
                .andExpect(jsonPath("$.terminal").value(false))
                .andExpect(jsonPath("$.timeline.length()").value(3));
    }

    @Test
    @DisplayName("The kitchen queue shows live tickets and is closed to customers")
    void kitchenQueueIsStaffOnly() throws Exception {
        Long orderId = placeDineInOrder();

        mockMvc.perform(get("/api/v1/orders/kitchen/queue")
                        .header("Authorization", bearer(CUSTOMER_ID, "casey", Role.CUSTOMER)))
                .andExpect(status().isForbidden());

        // PENDING tickets are not on the board yet.
        mockMvc.perform(get("/api/v1/orders/kitchen/queue")
                        .header("Authorization", bearer(2L, "kai", Role.KITCHEN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        advance(orderId, "CONFIRMED");

        mockMvc.perform(get("/api/v1/orders/kitchen/queue")
                        .header("Authorization", bearer(2L, "kai", Role.KITCHEN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("CONFIRMED"));
    }

    @Test
    @DisplayName("Requests without a token are rejected")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    private org.springframework.test.web.servlet.ResultActions advance(Long orderId, String status)
            throws Exception {
        return mockMvc.perform(patch("/api/v1/orders/" + orderId + "/status")
                .header("Authorization", bearer(2L, "kai", Role.KITCHEN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", status))));
    }

    private Long placeDineInOrder() throws Exception {
        return repository.findByOrderReference(placeDineInOrderAndReturnReference()).orElseThrow().getId();
    }

    private String placeDineInOrderAndReturnReference() throws Exception {
        String response = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", bearer(CUSTOMER_ID, "casey", Role.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "channel", "DINE_IN",
                                "tableNumber", "12",
                                "items", List.of(Map.of("menuItemId", PIZZA_ID, "quantity", 2))))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("orderReference").asText();
    }

    private String bearer(long userId, String username, Role role) {
        return "Bearer " + jwtService.generateToken(userId, username, username + "@example.com", role);
    }
}
