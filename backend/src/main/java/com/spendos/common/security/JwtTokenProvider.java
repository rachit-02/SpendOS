package com.spendos.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Issues and verifies HS256 JWTs. Claims follow SECURITY.md (iss, sub, email, roles, iat, exp, jti). */
@Component
public class JwtTokenProvider {

    private static final String TOKEN_TYPE = "tokenType";

    private final SecretKey signingKey;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;
    private final String issuer;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long accessTokenExpiration,
            @Value("${jwt.refresh-expiration}") long refreshTokenExpiration,
            @Value("${jwt.issuer}") String issuer) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 bytes");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
        this.issuer = issuer;
    }

    public String createAccessToken(UUID userId, String email) {
        return createToken(userId, email, accessTokenExpiration, "access");
    }

    public String createRefreshToken(UUID userId, String email) {
        return createToken(userId, email, refreshTokenExpiration, "refresh");
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isRefreshToken(Claims claims) {
        return "refresh".equals(claims.get(TOKEN_TYPE, String.class));
    }

    public boolean isAccessToken(Claims claims) {
        return "access".equals(claims.get(TOKEN_TYPE, String.class));
    }

    public UUID getUserId(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }

    public String getTokenId(Claims claims) {
        return claims.getId();
    }

    public Instant getExpiration(Claims claims) {
        return claims.getExpiration().toInstant();
    }

    public Instant getIssuedAt(Claims claims) {
        return claims.getIssuedAt() == null ? null : claims.getIssuedAt().toInstant();
    }

    public long getAccessTokenExpirationSeconds() {
        return accessTokenExpiration / 1000;
    }

    private String createToken(UUID userId, String email, long expiration, String tokenType) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(userId.toString())
                .claim("email", email)
                .claim("roles", List.of("USER"))
                .claim(TOKEN_TYPE, tokenType)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expiration)))
                .signWith(signingKey)
                .compact();
    }
}
