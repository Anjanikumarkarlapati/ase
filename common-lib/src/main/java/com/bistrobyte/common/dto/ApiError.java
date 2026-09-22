package com.bistrobyte.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/** Uniform error body returned by every BistroByte service. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(Instant timestamp,
                       int status,
                       String error,
                       String message,
                       String path,
                       List<FieldViolation> violations) {

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path, List.of());
    }

    public static ApiError of(int status, String error, String message, String path,
                              List<FieldViolation> violations) {
        return new ApiError(Instant.now(), status, error, message, path, violations);
    }

    public record FieldViolation(String field, String message) {
    }
}
