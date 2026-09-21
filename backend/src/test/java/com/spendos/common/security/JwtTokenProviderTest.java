package com.spendos.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private static final String SECRET = "unit-test-secret-that-is-longer-than-32-bytes";

    @Test
    void accessTokenCarriesDocumentedClaims() {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, 3_600_000, 2_592_000_000L, "spendos");
        UUID userId = UUID.randomUUID();

        Claims claims = provider.parseToken(provider.createAccessToken(userId, "a@example.com"));

        assertThat(claims.getIssuer()).isEqualTo("spendos");
        assertThat(provider.getUserId(claims)).isEqualTo(userId);
        assertThat(claims.get("email", String.class)).isEqualTo("a@example.com");
        assertThat(claims.get("roles", java.util.List.class)).containsExactly("USER");
        assertThat(claims.getId()).isNotBlank();
        assertThat(provider.isAccessToken(claims)).isTrue();
        assertThat(provider.isRefreshToken(claims)).isFalse();
        assertThat(provider.getAccessTokenExpirationSeconds()).isEqualTo(3600);
    }

    @Test
    void expiredTokenIsRejected() {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, -1_000, -1_000, "spendos");
        String token = provider.createAccessToken(UUID.randomUUID(), "a@example.com");

        assertThatThrownBy(() -> provider.parseToken(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tokenSignedWithDifferentKeyIsRejected() {
        JwtTokenProvider issuer = new JwtTokenProvider(SECRET, 60_000, 60_000, "spendos");
        JwtTokenProvider verifier = new JwtTokenProvider(SECRET + "-other", 60_000, 60_000, "spendos");
        String token = issuer.createAccessToken(UUID.randomUUID(), "a@example.com");

        assertThatThrownBy(() -> verifier.parseToken(token)).isInstanceOf(SignatureException.class);
    }

    @Test
    void shortSecretIsRefusedAtStartup() {
        assertThatThrownBy(() -> new JwtTokenProvider("too-short", 1, 1, "spendos"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
