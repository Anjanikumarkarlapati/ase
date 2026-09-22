package com.bistrobyte.common.security;

/**
 * Roles recognised across the BistroByte platform.
 * Authorities are persisted / signed as the plain enum name and exposed to
 * Spring Security as {@code ROLE_<NAME>}.
 */
public enum Role {

    /** End customer placing dine-in, takeaway or delivery orders. */
    CUSTOMER,

    /** Front-of-house staff: takes orders, advances order status, marks items sold out. */
    STAFF,

    /** Kitchen execution display: sees the queue and moves tickets through preparation. */
    KITCHEN,

    /** Full administrative access, including menu authoring and user management. */
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }

    public static Role from(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Role must not be null");
        }
        String normalised = value.trim().toUpperCase();
        if (normalised.startsWith("ROLE_")) {
            normalised = normalised.substring("ROLE_".length());
        }
        return Role.valueOf(normalised);
    }
}
