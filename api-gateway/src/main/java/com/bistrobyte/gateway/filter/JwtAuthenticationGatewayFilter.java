package com.bistrobyte.gateway.filter;

import com.bistrobyte.gateway.security.GatewayJwtProperties;
import com.bistrobyte.gateway.security.GatewayTokenVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Edge authentication. Unprotected paths pass straight through; everything else must
 * carry a valid Bearer token. The verified identity is stripped from the inbound request
 * and re-added as trusted {@code X-Auth-*} headers so downstream services never read
 * client-supplied identity headers.
 */
@Component
public class JwtAuthenticationGatewayFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationGatewayFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    public static final String HEADER_USER_ID = "X-Auth-User-Id";
    public static final String HEADER_USERNAME = "X-Auth-Username";
    public static final String HEADER_ROLE = "X-Auth-Role";

    private final GatewayTokenVerifier tokenVerifier;
    private final GatewayJwtProperties properties;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public JwtAuthenticationGatewayFilter(GatewayTokenVerifier tokenVerifier,
                                          GatewayJwtProperties properties,
                                          ObjectMapper objectMapper) {
        this.tokenVerifier = tokenVerifier;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (isPublic(path)) {
            return chain.filter(exchange.mutate().request(stripIdentityHeaders(request)).build());
        }

        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return reject(exchange, HttpStatus.UNAUTHORIZED,
                    "Missing Authorization header. Expected 'Bearer <token>'.");
        }

        Claims claims = tokenVerifier.verify(header.substring(BEARER_PREFIX.length()).trim());
        if (claims == null) {
            return reject(exchange, HttpStatus.UNAUTHORIZED, "The access token is invalid or has expired.");
        }

        String role = asRole(claims.get("role", String.class));
        if (role == null) {
            return reject(exchange, HttpStatus.UNAUTHORIZED, "The access token carries no usable role claim.");
        }

        if (!isRoleAllowed(path, request.getMethod().name(), role)) {
            log.debug("Edge denied {} {} for role {}", request.getMethod(), path, role);
            return reject(exchange, HttpStatus.FORBIDDEN,
                    "Role " + role + " is not permitted to call this endpoint.");
        }

        Object userId = claims.get("uid");
        ServerHttpRequest mutated = request.mutate()
                .headers(headers -> {
                    headers.remove(HEADER_USER_ID);
                    headers.remove(HEADER_USERNAME);
                    headers.remove(HEADER_ROLE);
                    if (userId != null) {
                        headers.add(HEADER_USER_ID, String.valueOf(userId));
                    }
                    headers.add(HEADER_USERNAME, claims.getSubject());
                    headers.add(HEADER_ROLE, role);
                })
                .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    private ServerHttpRequest stripIdentityHeaders(ServerHttpRequest request) {
        return request.mutate()
                .headers(headers -> {
                    headers.remove(HEADER_USER_ID);
                    headers.remove(HEADER_USERNAME);
                    headers.remove(HEADER_ROLE);
                })
                .build();
    }

    private boolean isPublic(String path) {
        return properties.getPublicPaths().stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private static String asRole(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        return value.startsWith("ROLE_") ? value.substring("ROLE_".length()) : value;
    }

    /** A request is allowed unless a matching rule exists that excludes the caller's role. */
    private boolean isRoleAllowed(String path, String method, String role) {
        List<GatewayJwtProperties.RoleRule> matching = properties.getRoleRules().stream()
                .filter(rule -> rule.getPattern() != null && pathMatcher.match(rule.getPattern(), path))
                .filter(rule -> rule.getMethods().isEmpty() || rule.getMethods().stream()
                        .anyMatch(candidate -> candidate.equalsIgnoreCase(method)))
                .toList();
        if (matching.isEmpty()) {
            return true;
        }
        return matching.stream().anyMatch(rule -> rule.getRoles().stream()
                .anyMatch(allowed -> asRole(allowed) != null && asRole(allowed).equals(role)));
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of(
                "timestamp", Instant.now().toString(),
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", message,
                "path", exchange.getRequest().getURI().getPath());
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (Exception ex) {
            bytes = ("{\"message\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
