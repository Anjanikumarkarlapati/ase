package com.bistrobyte.userservice;

import com.bistrobyte.common.security.JwtService;
import com.bistrobyte.common.security.Role;
import com.bistrobyte.userservice.domain.UserAccount;
import com.bistrobyte.userservice.repository.UserAccountRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end checks over the registration, login and profile endpoints. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserAccountRepository repository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @BeforeEach
    void resetDatabase() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("Registration creates a CUSTOMER account and never returns the password")
    void registersCustomer() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "diner01",
                                "email", "diner01@example.com",
                                "password", "Password@123",
                                "fullName", "Dana Diner"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("diner01"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        assertThat(repository.existsByUsernameIgnoreCase("diner01")).isTrue();
    }

    @Test
    @DisplayName("An anonymous caller cannot self-provision an ADMIN account")
    void rejectsAnonymousRoleEscalation() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "sneaky",
                                "email", "sneaky@example.com",
                                "password", "Password@123",
                                "fullName", "Sneaky Sam",
                                "role", "ADMIN"))))
                .andExpect(status().isForbidden());

        assertThat(repository.existsByUsernameIgnoreCase("sneaky")).isFalse();
    }

    @Test
    @DisplayName("Duplicate usernames are rejected with 409")
    void rejectsDuplicateUsername() throws Exception {
        seedUser("diner01", "diner01@example.com", Role.CUSTOMER);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "diner01",
                                "email", "other@example.com",
                                "password", "Password@123",
                                "fullName", "Someone Else"))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Invalid payloads are rejected with field-level violations")
    void rejectsInvalidRegistration() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "a",
                                "email", "not-an-email",
                                "password", "short",
                                "fullName", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations").isArray());
    }

    @Test
    @DisplayName("Login returns a usable bearer token")
    void loginIssuesToken() throws Exception {
        seedUser("diner01", "diner01@example.com", Role.CUSTOMER);

        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "usernameOrEmail", "diner01",
                                "password", "Password@123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.username").value("diner01"))
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(response).get("accessToken").asText();
        assertThat(jwtService.resolve(token)).isNotNull();
        assertThat(jwtService.resolve(token).role()).isEqualTo(Role.CUSTOMER);

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("diner01"));
    }

    @Test
    @DisplayName("Login by email also works, and a wrong password gives 401")
    void loginByEmailAndWrongPassword() throws Exception {
        seedUser("diner01", "diner01@example.com", Role.CUSTOMER);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "usernameOrEmail", "diner01@example.com",
                                "password", "Password@123"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "usernameOrEmail", "diner01",
                                "password", "WrongPassword1"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A protected endpoint without a token returns a JSON 401")
    void rejectsMissingToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("A customer token cannot list accounts, an admin token can")
    void enforcesRoleOnUserListing() throws Exception {
        UserAccount customer = seedUser("diner01", "diner01@example.com", Role.CUSTOMER);
        UserAccount admin = seedUser("boss", "boss@example.com", Role.ADMIN);

        mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + tokenFor(customer)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + tokenFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("A deactivated account can no longer log in")
    void deactivatedAccountCannotLogIn() throws Exception {
        UserAccount customer = seedUser("diner01", "diner01@example.com", Role.CUSTOMER);
        UserAccount admin = seedUser("boss", "boss@example.com", Role.ADMIN);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/users/" + customer.getId())
                        .header("Authorization", "Bearer " + tokenFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "usernameOrEmail", "diner01",
                                "password", "Password@123"))))
                .andExpect(status().isConflict());
    }

    private String tokenFor(UserAccount account) {
        return jwtService.generateToken(account.getId(), account.getUsername(),
                account.getEmail(), account.getRole());
    }

    private UserAccount seedUser(String username, String email, Role role) {
        UserAccount account = new UserAccount();
        account.setUsername(username);
        account.setEmail(email);
        account.setFullName("Test " + username);
        account.setPasswordHash(passwordEncoder.encode("Password@123"));
        account.setRole(role);
        account.setActive(true);
        return repository.save(account);
    }
}
