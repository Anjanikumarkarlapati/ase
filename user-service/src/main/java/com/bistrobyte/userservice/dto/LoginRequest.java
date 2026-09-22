package com.bistrobyte.userservice.dto;

import jakarta.validation.constraints.NotBlank;

/** Accepts either the username or the email address in {@code usernameOrEmail}. */
public record LoginRequest(

        @NotBlank(message = "Username or email is required")
        String usernameOrEmail,

        @NotBlank(message = "Password is required")
        String password) {
}
