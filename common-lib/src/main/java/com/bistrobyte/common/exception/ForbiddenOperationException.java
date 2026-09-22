package com.bistrobyte.common.exception;

/** Thrown when the caller is authenticated but not allowed to touch the target resource. */
public class ForbiddenOperationException extends RuntimeException {

    public ForbiddenOperationException(String message) {
        super(message);
    }
}
