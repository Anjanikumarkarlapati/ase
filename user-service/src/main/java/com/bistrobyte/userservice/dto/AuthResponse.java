package com.bistrobyte.userservice.dto;

/** Issued access token plus the profile the client needs to render its shell. */
public record AuthResponse(String accessToken,
                           String tokenType,
                           long expiresInSeconds,
                           UserResponse user) {

    public static AuthResponse of(String token, long expiresInSeconds, UserResponse user) {
        return new AuthResponse(token, "Bearer", expiresInSeconds, user);
    }
}
