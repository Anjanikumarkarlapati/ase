package com.bistrobyte.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Issues and validates the HS256 access tokens used by every BistroByte service.
 */
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    public static final String CLAIM_USER_ID = "uid";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_EMAIL = "email";

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = buildKey(properties.getSecret());
    }

    private static SecretKey buildKey(String secret) {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret);
        } catch (RuntimeException ex) {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "bistrobyte.security.jwt.secret must be at least 32 bytes for HS256 signing");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /** Mints a signed access token for the given identity. */
    public String generateToken(Long userId, String username, String email, Role role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + properties.getExpirationMs());
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_USER_ID, userId);
        claims.put(CLAIM_ROLE, role.name());
        claims.put(CLAIM_EMAIL, email);
        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuer(properties.getIssuer())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    /** @return the verified claim set, or {@code null} when the token is invalid or expired. */
    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(properties.getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Rejected JWT: {}", ex.getMessage());
            return null;
        }
    }

    /** @return the authenticated identity, or {@code null} when the token cannot be trusted. */
    public AuthenticatedUser resolve(String token) {
        Claims claims = parse(token);
        if (claims == null) {
            return null;
        }
        try {
            Number uid = claims.get(CLAIM_USER_ID, Number.class);
            Role role = Role.from(claims.get(CLAIM_ROLE, String.class));
            return new AuthenticatedUser(
                    uid == null ? null : uid.longValue(),
                    claims.getSubject(),
                    claims.get(CLAIM_EMAIL, String.class),
                    role);
        } catch (RuntimeException ex) {
            log.debug("JWT carried unusable claims: {}", ex.getMessage());
            return null;
        }
    }

    public long getExpirationMs() {
        return properties.getExpirationMs();
    }
}
