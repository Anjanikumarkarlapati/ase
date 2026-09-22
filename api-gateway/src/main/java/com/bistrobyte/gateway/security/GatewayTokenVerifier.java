package com.bistrobyte.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/** Verifies the signature, issuer and expiry of tokens presented at the edge. */
@Component
public class GatewayTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(GatewayTokenVerifier.class);

    private final SecretKey signingKey;
    private final String issuer;

    public GatewayTokenVerifier(GatewayJwtProperties properties) {
        this.issuer = properties.getIssuer();
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

    /** @return verified claims, or {@code null} when the token must be rejected. */
    public Claims verify(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Gateway rejected token: {}", ex.getMessage());
            return null;
        }
    }
}
