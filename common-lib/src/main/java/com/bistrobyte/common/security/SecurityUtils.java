package com.bistrobyte.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/** Convenience access to the caller identity placed in the context by the JWT filter. */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<AuthenticatedUser> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return Optional.empty();
        }
        return Optional.of(user);
    }

    public static AuthenticatedUser requireCurrentUser() {
        return currentUser().orElseThrow(
                () -> new IllegalStateException("No authenticated BistroByte user in the security context"));
    }

    public static boolean hasRole(Role role) {
        return currentUser().map(user -> user.role() == role).orElse(false);
    }

    /** Staff, kitchen and admin accounts may act on any order; customers only on their own. */
    public static boolean isStaffLevel() {
        return currentUser()
                .map(user -> user.role() == Role.STAFF || user.role() == Role.KITCHEN || user.role() == Role.ADMIN)
                .orElse(false);
    }
}
