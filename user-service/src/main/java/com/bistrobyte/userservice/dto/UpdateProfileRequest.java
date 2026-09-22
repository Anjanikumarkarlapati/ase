package com.bistrobyte.userservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(

        @NotBlank(message = "Full name is required")
        @Size(max = 120)
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid address")
        @Size(max = 160)
        String email,

        @Pattern(regexp = "^$|^\\+?[0-9 -]{7,20}$", message = "Phone number is not valid")
        String phoneNumber) {
}
