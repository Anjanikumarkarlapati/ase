package com.bistrobyte.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Shared signing configuration. Every service validates tokens with the same
 * secret, so the value must be identical across the platform.
 */
@ConfigurationProperties(prefix = "bistrobyte.security.jwt")
public class JwtProperties {

    /** HMAC signing secret. Must be at least 32 bytes for HS256. */
    private String secret = "bistrobyte-super-secret-signing-key-change-me-in-production";

    /** Access token lifetime in milliseconds (default 2 hours). */
    private long expirationMs = 7_200_000L;

    /** Issuer claim written into, and verified on, every token. */
    private String issuer = "bistrobyte-user-service";

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public void setExpirationMs(long expirationMs) {
        this.expirationMs = expirationMs;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }
}
