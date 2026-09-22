package com.bistrobyte.common.security;

/**
 * Identity extracted from a verified JWT and stored as the authentication principal.
 */
public record AuthenticatedUser(Long userId, String username, String email, Role role) {
}
