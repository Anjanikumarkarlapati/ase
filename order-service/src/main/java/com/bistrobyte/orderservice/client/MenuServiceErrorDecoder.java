package com.bistrobyte.orderservice.client;

import com.bistrobyte.common.exception.BusinessRuleException;
import com.bistrobyte.common.exception.ForbiddenOperationException;
import com.bistrobyte.common.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.ErrorDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Turns a menu-service error into the same domain exception the order service would raise
 * itself, so a sold-out dish surfaces to the customer as a 409 with the real reason
 * instead of an opaque 500.
 */
public class MenuServiceErrorDecoder implements ErrorDecoder {

    private static final Logger log = LoggerFactory.getLogger(MenuServiceErrorDecoder.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ErrorDecoder defaultDecoder = new Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        String message = extractMessage(response);
        log.warn("Menu service returned {} for {}: {}", response.status(), methodKey, message);
        return switch (response.status()) {
            case 400, 409 -> new BusinessRuleException(message);
            case 401, 403 -> new ForbiddenOperationException(
                    "The menu service rejected this request: " + message);
            case 404 -> new ResourceNotFoundException(message);
            default -> defaultDecoder.decode(methodKey, response);
        };
    }

    private String extractMessage(Response response) {
        if (response.body() == null) {
            return "The menu service is unavailable";
        }
        try (InputStream body = response.body().asInputStream()) {
            String raw = new String(body.readAllBytes(), StandardCharsets.UTF_8);
            if (raw.isBlank()) {
                return "The menu service returned status " + response.status();
            }
            JsonNode node = objectMapper.readTree(raw);
            JsonNode message = node.get("message");
            return (message == null || message.isNull()) ? raw : message.asText();
        } catch (IOException ex) {
            return "The menu service returned status " + response.status();
        }
    }
}
