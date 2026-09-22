package com.bistrobyte.orderservice.client;

import feign.Logger;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Not annotated with {@code @Configuration}: it is referenced explicitly from
 * {@code @FeignClient} so it applies to the menu client only, not to the whole context.
 */
public class FeignClientConfiguration {

    private static final String HEADER_CORRELATION_ID = "X-Correlation-Id";

    /**
     * Propagates the caller's bearer token so the menu service sees the same principal.
     * Without this the downstream call would be rejected as anonymous.
     */
    @Bean
    public RequestInterceptor bearerTokenRelayInterceptor() {
        return template -> {
            if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
                return;
            }
            String authorization = attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
            if (authorization != null && !authorization.isBlank()
                    && !template.headers().containsKey(HttpHeaders.AUTHORIZATION)) {
                template.header(HttpHeaders.AUTHORIZATION, authorization);
            }
            String correlationId = attributes.getRequest().getHeader(HEADER_CORRELATION_ID);
            if (correlationId != null && !correlationId.isBlank()) {
                template.header(HEADER_CORRELATION_ID, correlationId);
            }
        };
    }

    @Bean
    public ErrorDecoder menuServiceErrorDecoder() {
        return new MenuServiceErrorDecoder();
    }

    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }
}
