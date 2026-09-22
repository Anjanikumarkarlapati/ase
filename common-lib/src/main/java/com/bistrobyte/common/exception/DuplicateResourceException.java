package com.bistrobyte.common.exception;

/** Thrown when creating an entity would break a uniqueness constraint. Mapped to HTTP 409. */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
