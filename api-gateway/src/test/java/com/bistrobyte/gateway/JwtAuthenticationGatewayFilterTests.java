package com.bistrobyte.gateway;

import com.bistrobyte.gateway.filter.JwtAuthenticationGatewayFilter;
import com.bistrobyte.gateway.security.GatewayJwtProperties;
import com.bistrobyte.gateway.security.GatewayTokenVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** Edge authentication: which requests get through, and what identity is forwarded. */
class JwtAuthenticationGatewayFilterTests {

    private static final String SECRET = "gateway-unit-test-signing-secret-0123456789";
    private static final String ISSUER = "bistrobyte-user-service";

    private final SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    private JwtAuthenticationGatewayFilter filter;

    @BeforeEach
    void setUp() {
        GatewayJwtProperties properties = new GatewayJwtProperties();
        properties.setSecret(SECRET);
        properties.setIssuer(ISSUER);
        properties.setPublicPaths(List.of("/api/v1/auth/login", "/api/v1/auth/register", "/actuator/**"));

        GatewayJwtProperties.RoleRule menuWrites = new GatewayJwtProperties.RoleRule();
        menuWrites.setPattern("/api/v1/menu/items/**");
        menuWrites.setMethods(List.of("POST", "PUT", "DELETE"));
        menuWrites.setRoles(List.of("ADMIN", "STAFF"));
        properties.setRoleRules(List.of(menuWrites));

        filter = new JwtAuthenticationGatewayFilter(
                new GatewayTokenVerifier(properties), properties, new ObjectMapper());
    }

    @Test
    @DisplayName("Login is served without a token")
    void allowsPublicPath() {
        MockServerWebExchange exchange = exchange(HttpMethod.POST, "/api/v1/auth/login", null);
        AtomicReference<ServerHttpRequest> forwarded = new AtomicReference<>();

        filter.filter(exchange, capturing(forwarded)).block();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
        assertThat(forwarded.get()).isNotNull();
    }

    @Test
    @DisplayName("A protected path without a token is rejected with 401")
    void rejectsMissingToken() {
        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/api/v1/orders", null);

        filter.filter(exchange, chain -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("A token signed with the wrong key is rejected with 401")
    void rejectsForgedToken() {
        String forged = Jwts.builder()
                .subject("attacker")
                .issuer(ISSUER)
                .claims(Map.of("uid", 1, "role", "ADMIN"))
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor("a-completely-different-signing-secret-123456".getBytes(
                        StandardCharsets.UTF_8)))
                .compact();
        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/api/v1/orders", forged);

        filter.filter(exchange, chain -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("An expired token is rejected with 401")
    void rejectsExpiredToken() {
        String expired = token("casey", "CUSTOMER", 42L, -60_000);
        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/api/v1/orders", expired);

        filter.filter(exchange, chain -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("A valid token is forwarded with trusted identity headers")
    void forwardsIdentityHeaders() {
        MockServerWebExchange exchange = exchange(
                HttpMethod.GET, "/api/v1/orders", token("casey", "CUSTOMER", 42L, 60_000));
        AtomicReference<ServerHttpRequest> forwarded = new AtomicReference<>();

        filter.filter(exchange, capturing(forwarded)).block();

        HttpHeaders headers = forwarded.get().getHeaders();
        assertThat(headers.getFirst(JwtAuthenticationGatewayFilter.HEADER_USER_ID)).isEqualTo("42");
        assertThat(headers.getFirst(JwtAuthenticationGatewayFilter.HEADER_USERNAME)).isEqualTo("casey");
        assertThat(headers.getFirst(JwtAuthenticationGatewayFilter.HEADER_ROLE)).isEqualTo("CUSTOMER");
    }

    @Test
    @DisplayName("Client-supplied identity headers are stripped and replaced")
    void stripsSpoofedIdentityHeaders() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .method(HttpMethod.GET, "/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("casey", "CUSTOMER", 42L, 60_000))
                .header(JwtAuthenticationGatewayFilter.HEADER_ROLE, "ADMIN")
                .header(JwtAuthenticationGatewayFilter.HEADER_USER_ID, "1")
                .build());
        AtomicReference<ServerHttpRequest> forwarded = new AtomicReference<>();

        filter.filter(exchange, capturing(forwarded)).block();

        HttpHeaders headers = forwarded.get().getHeaders();
        assertThat(headers.get(JwtAuthenticationGatewayFilter.HEADER_ROLE)).containsExactly("CUSTOMER");
        assertThat(headers.get(JwtAuthenticationGatewayFilter.HEADER_USER_ID)).containsExactly("42");
    }

    @Test
    @DisplayName("Edge role rules keep customers out of menu authoring")
    void enforcesRoleRules() {
        MockServerWebExchange denied = exchange(
                HttpMethod.POST, "/api/v1/menu/items", token("casey", "CUSTOMER", 42L, 60_000));
        filter.filter(denied, chain -> Mono.empty()).block();
        assertThat(denied.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        MockServerWebExchange allowed = exchange(
                HttpMethod.POST, "/api/v1/menu/items", token("ava", "ADMIN", 1L, 60_000));
        AtomicReference<ServerHttpRequest> forwarded = new AtomicReference<>();
        filter.filter(allowed, capturing(forwarded)).block();
        assertThat(allowed.getResponse().getStatusCode()).isNull();
        assertThat(forwarded.get()).isNotNull();
    }

    @Test
    @DisplayName("Reads of the menu are open to any authenticated role")
    void allowsReadsForAnyRole() {
        MockServerWebExchange exchange = exchange(
                HttpMethod.GET, "/api/v1/menu/items", token("casey", "CUSTOMER", 42L, 60_000));
        AtomicReference<ServerHttpRequest> forwarded = new AtomicReference<>();

        filter.filter(exchange, capturing(forwarded)).block();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
        assertThat(forwarded.get()).isNotNull();
    }

    private GatewayFilterChain capturing(AtomicReference<ServerHttpRequest> sink) {
        return (ServerWebExchange exchange) -> {
            sink.set(exchange.getRequest());
            return Mono.empty();
        };
    }

    private MockServerWebExchange exchange(HttpMethod method, String path, String token) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.method(method, path);
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return MockServerWebExchange.from(builder.build());
    }

    private String token(String username, String role, long userId, long ttlMillis) {
        return Jwts.builder()
                .subject(username)
                .issuer(ISSUER)
                .claims(Map.of("uid", userId, "role", role, "email", username + "@example.com"))
                .issuedAt(new Date(System.currentTimeMillis() - 1000))
                .expiration(new Date(System.currentTimeMillis() + ttlMillis))
                .signWith(key)
                .compact();
    }
}
