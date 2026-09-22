package com.bistrobyte.userservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Self-service sign-up. The role is optional and only honoured for privileged callers;
 * anonymous registration always produces a CUSTOMER account.
 */
public record RegistrationRequest(

        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 60, message = "Username must be between 3 and 60 characters")
        @Pattern(regexp = "^[A-Za-z0-9._-]+$",
                message = "Username may only contain letters, digits, dots, underscores and hyphens")
        String username,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid address")
        @Size(max = 160)
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        String password,

        @NotBlank(message = "Full name is required")
        @Size(max = 120)
        String fullName,

        @Pattern(regexp = "^$|^\\+?[0-9 -]{7,20}$", message = "Phone number is not valid")
        String phoneNumber,

        /** CUSTOMER, STAFF, KITCHEN or ADMIN. Ignored unless an ADMIN is calling. */
        String role) {
}
