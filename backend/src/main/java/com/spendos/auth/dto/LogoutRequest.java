package com.spendos.auth.dto;

/** Optional body for logout; when present the refresh token is revoked along with the access token. */
public record LogoutRequest(String refreshToken) {
}
