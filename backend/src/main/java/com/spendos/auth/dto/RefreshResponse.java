package com.spendos.auth.dto;

public record RefreshResponse(String accessToken, String refreshToken, long expiresIn) {
}
