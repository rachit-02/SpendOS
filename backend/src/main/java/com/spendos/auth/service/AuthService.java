package com.spendos.auth.service;

import com.spendos.auth.dto.AuthResponse;
import com.spendos.auth.dto.LoginRequest;
import com.spendos.auth.dto.RegisterRequest;
import com.spendos.auth.dto.RefreshRequest;
import com.spendos.auth.dto.RefreshResponse;
import com.spendos.auth.dto.UserResponse;
import com.spendos.auth.entity.User;
import com.spendos.auth.entity.RevokedToken;
import com.spendos.auth.dto.LogoutRequest;
import com.spendos.auth.repository.UserRepository;
import com.spendos.auth.repository.RevokedTokenRepository;
import com.spendos.common.security.JwtTokenProvider;
import java.util.Locale;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final RevokedTokenRepository revokedTokenRepository;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider, RevokedTokenRepository revokedTokenRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.revokedTokenRepository = revokedTokenRepository;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("Email is already registered");
        }
        validatePassword(request.password(), email);
        User user = new User(email, passwordEncoder.encode(request.password()), request.fullName().trim());
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(normalizeEmail(request.email()))
                .filter(User::isActive)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }
        return new AuthResponse(
                tokenProvider.createAccessToken(user.getId(), user.getEmail()),
                tokenProvider.createRefreshToken(user.getId(), user.getEmail()),
                tokenProvider.getAccessTokenExpirationSeconds(),
                UserResponse.from(user));
    }

    @Transactional
    public RefreshResponse refresh(RefreshRequest request) {
        final var claims = parseRefreshToken(request.refreshToken());
        if (!tokenProvider.isRefreshToken(claims)) {
            throw new BadCredentialsException("Invalid refresh token");
        }
        if (revokedTokenRepository.existsById(tokenProvider.getTokenId(claims))) {
            throw new BadCredentialsException("Invalid refresh token");
        }
        revoke(claims);
        User user = userRepository.findByIdAndDeletedAtIsNull(tokenProvider.getUserId(claims))
                .filter(User::isActive)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
        return new RefreshResponse(
                tokenProvider.createAccessToken(user.getId(), user.getEmail()),
                tokenProvider.createRefreshToken(user.getId(), user.getEmail()),
                tokenProvider.getAccessTokenExpirationSeconds());
    }

    @Transactional
    public void logout(LogoutRequest request) {
        io.jsonwebtoken.Claims claims = parseRefreshToken(request.refreshToken());
        if (!tokenProvider.isRefreshToken(claims)) {
            throw new BadCredentialsException("Invalid refresh token");
        }
        revoke(claims);
    }

    private void revoke(io.jsonwebtoken.Claims claims) {
        revokedTokenRepository.save(new RevokedToken(
                tokenProvider.getTokenId(claims), tokenProvider.getExpiration(claims)));
    }

    private io.jsonwebtoken.Claims parseRefreshToken(String token) {
        try {
            return tokenProvider.parseToken(token);
        } catch (RuntimeException exception) {
            throw new BadCredentialsException("Invalid refresh token", exception);
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void validatePassword(String password, String email) {
        boolean complex = password.chars().anyMatch(Character::isUpperCase)
                && password.chars().anyMatch(Character::isLowerCase)
                && password.chars().anyMatch(Character::isDigit)
                && password.chars().anyMatch(character -> !Character.isLetterOrDigit(character));
        if (!complex || password.toLowerCase(Locale.ROOT).contains(email.split("@")[0])) {
            throw new IllegalArgumentException("Password must include uppercase, lowercase, number, and symbol and must not contain the email name");
        }
    }
}
