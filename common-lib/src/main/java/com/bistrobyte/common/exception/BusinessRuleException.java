package com.bistrobyte.common.exception;

/**
 * Thrown when a request is well formed but violates a domain rule, such as ordering a
 * sold-out dish or moving an order to an unreachable status. Mapped to HTTP 409.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
