package com.bistrobyte.userservice.dto;

import com.bistrobyte.common.security.Role;
import com.bistrobyte.userservice.domain.UserAccount;

import java.time.Instant;

/** Public view of an account. Never carries the password hash. */
public record UserResponse(Long id,
                           String username,
                           String email,
                           String fullName,
                           String phoneNumber,
                           Role role,
                           boolean active,
                           Instant createdAt) {

    public static UserResponse from(UserAccount account) {
        return new UserResponse(
                account.getId(),
                account.getUsername(),
                account.getEmail(),
                account.getFullName(),
                account.getPhoneNumber(),
                account.getRole(),
                account.isActive(),
                account.getCreatedAt());
    }
}
