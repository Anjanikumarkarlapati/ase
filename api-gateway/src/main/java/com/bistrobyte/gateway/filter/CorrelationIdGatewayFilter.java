package com.bistrobyte.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Stamps every request with a correlation id so a single customer order can be traced
 * across the gateway, the order service and the menu service log lines.
 */
@Component
public class CorrelationIdGatewayFilter implements GlobalFilter, Ordered {

    public static final String HEADER_CORRELATION_ID = "X-Correlation-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(HEADER_CORRELATION_ID);
        String resolved = (correlationId == null || correlationId.isBlank())
                ? UUID.randomUUID().toString()
                : correlationId;
        exchange.getResponse().getHeaders().set(HEADER_CORRELATION_ID, resolved);
        return chain.filter(exchange.mutate()
                .request(exchange.getRequest().mutate()
                        .header(HEADER_CORRELATION_ID, resolved)
                        .build())
                .build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
