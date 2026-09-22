package com.bistrobyte.menuservice;

import com.bistrobyte.common.security.Role;
import com.bistrobyte.menuservice.domain.Category;
import com.bistrobyte.menuservice.domain.MenuItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Role enforcement and filtering over the menu HTTP surface. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MenuApiTests extends MenuTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private Category mains;

    @BeforeEach
    void setUp() {
        clearMenu();
        mains = givenCategory("Mains");
    }

    @Test
    @DisplayName("Browsing the menu requires a token")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/menu/items"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Customers see only orderable dishes by default")
    void hidesSoldOutDishesFromCustomers() throws Exception {
        givenItem(mains, "Margherita Pizza", "11.90", null);
        MenuItem soldOut = givenItem(mains, "Tiramisu", "6.40", 0);
        soldOut.setAvailable(false);
        menuItemRepository.save(soldOut);

        mockMvc.perform(get("/api/v1/menu/items").header("Authorization", bearer(Role.CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Margherita Pizza"));

        mockMvc.perform(get("/api/v1/menu/items")
                        .param("orderableOnly", "false")
                        .header("Authorization", bearer(Role.STAFF)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("A customer cannot author the menu, an admin can")
    void enforcesWriteRoles() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "New Biryani");
        payload.put("description", "Slow-cooked, served with raita");
        payload.put("price", "13.25");
        payload.put("categoryId", mains.getId());
        payload.put("stockQuantity", 12);
        payload.put("preparationMinutes", 35);

        mockMvc.perform(post("/api/v1/menu/items")
                        .header("Authorization", bearer(Role.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/menu/items")
                        .header("Authorization", bearer(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("New Biryani"))
                .andExpect(jsonPath("$.orderable").value(true));
    }

    @Test
    @DisplayName("An invalid price is rejected with a field violation")
    void validatesPayload() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "");
        payload.put("price", "0.00");
        payload.put("categoryId", mains.getId());

        mockMvc.perform(post("/api/v1/menu/items")
                        .header("Authorization", bearer(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations").isArray());
    }

    @Test
    @DisplayName("Kitchen staff can 86 a dish mid-service")
    void kitchenCanMarkSoldOut() throws Exception {
        MenuItem item = givenItem(mains, "Butter Chicken", "14.50", 10);

        mockMvc.perform(patch("/api/v1/menu/items/" + item.getId() + "/availability")
                        .header("Authorization", bearer(Role.KITCHEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("available", false, "reason", "Ran out of gravy"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.orderable").value(false));
    }

    @Test
    @DisplayName("Restocking a sold-out dish puts it back on the menu")
    void restockRestoresAvailability() throws Exception {
        MenuItem item = givenItem(mains, "Tiramisu", "6.40", 0);
        item.setAvailable(false);
        menuItemRepository.save(item);

        mockMvc.perform(patch("/api/v1/menu/items/" + item.getId() + "/stock")
                        .header("Authorization", bearer(Role.STAFF))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("stockQuantity", 6, "unlimited", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockQuantity").value(6))
                .andExpect(jsonPath("$.orderable").value(true));
    }

    @Test
    @DisplayName("Fetching a dish that does not exist returns a JSON 404")
    void unknownDishReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/menu/items/424242").header("Authorization", bearer(Role.CUSTOMER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("A sold-out basket is rejected with 409 over HTTP")
    void reservationConflictSurfacesAs409() throws Exception {
        MenuItem item = givenItem(mains, "Lamb Seekh", "17.75", 1);

        mockMvc.perform(post("/api/v1/menu/inventory/reserve")
                        .header("Authorization", bearer(Role.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "orderReference", "BB-TEST-9001",
                                "lines", java.util.List.of(
                                        Map.of("menuItemId", item.getId(), "quantity", 4))))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Lamb Seekh")));
    }
}
