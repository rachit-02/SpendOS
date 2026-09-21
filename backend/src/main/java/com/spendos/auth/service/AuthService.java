package com.spendos.auth.service;

import com.spendos.auth.dto.AuthResponse;
import com.spendos.auth.dto.LoginRequest;
import com.spendos.auth.dto.LogoutRequest;
import com.spendos.auth.dto.RefreshRequest;
import com.spendos.auth.dto.RefreshResponse;
import com.spendos.auth.dto.RegisterRequest;
import com.spendos.auth.dto.UserResponse;
import com.spendos.auth.entity.RevokedToken;
import com.spendos.auth.entity.User;
import com.spendos.auth.repository.RevokedTokenRepository;
import com.spendos.auth.repository.UserRepository;
import com.spendos.common.exception.ApiException;
import com.spendos.common.security.JwtTokenProvider;
import com.spendos.users.service.UserPreferencesService;
import io.jsonwebtoken.Claims;
import java.time.Instant;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String INVALID_CREDENTIALS = "Invalid email or password";
    private static final String INVALID_REFRESH = "Invalid or expired refresh token";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final RevokedTokenRepository revokedTokenRepository;
    private final PasswordPolicy passwordPolicy;
    private final UserPreferencesService preferencesService;
    // Compared against when the email is unknown so login timing does not reveal registered emails.
    private final String dummyHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider, RevokedTokenRepository revokedTokenRepository,
                       PasswordPolicy passwordPolicy, UserPreferencesService preferencesService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.revokedTokenRepository = revokedTokenRepository;
        this.passwordPolicy = passwordPolicy;
        this.preferencesService = preferencesService;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing-equalization");
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.badRequest("DUPLICATE_EMAIL", "Email is already registered");
        }
        passwordPolicy.validate(request.password(), email);
        User user = userRepository.save(
                new User(email, passwordEncoder.encode(request.password()), request.fullName().trim()));
        preferencesService.createDefaults(user.getId());
        log.info("User registered | userId={} | action=register", user.getId());
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(normalizeEmail(request.email()))
                .filter(User::isActive)
                .orElse(null);
        String hash = user != null ? user.getPasswordHash() : dummyHash;
        boolean matches = passwordEncoder.matches(request.password(), hash);
        if (user == null || !matches) {
            log.warn("Failed login attempt | userKnown={} | action=failed_login", user != null);
            throw new BadCredentialsException(INVALID_CREDENTIALS);
        }
        log.info("User logged in | userId={} | action=login", user.getId());
        return new AuthResponse(
                tokenProvider.createAccessToken(user.getId(), user.getEmail()),
                tokenProvider.createRefreshToken(user.getId(), user.getEmail()),
                tokenProvider.getAccessTokenExpirationSeconds(),
                UserResponse.from(user));
    }

    /** Single-use refresh: the presented refresh token is revoked and a new pair is issued. */
    @Transactional
    public RefreshResponse refresh(RefreshRequest request) {
        Claims claims = parse(request.refreshToken());
        if (!tokenProvider.isRefreshToken(claims)
                || revokedTokenRepository.existsById(tokenProvider.getTokenId(claims))) {
            throw ApiException.unauthorized(INVALID_REFRESH);
        }
        User user = userRepository.findByIdAndDeletedAtIsNull(tokenProvider.getUserId(claims))
                .filter(candidate -> candidate.acceptsTokenIssuedAt(tokenProvider.getIssuedAt(claims)))
                .orElseThrow(() -> ApiException.unauthorized(INVALID_REFRESH));
        revoke(claims);
        return new RefreshResponse(
                tokenProvider.createAccessToken(user.getId(), user.getEmail()),
                tokenProvider.createRefreshToken(user.getId(), user.getEmail()),
                tokenProvider.getAccessTokenExpirationSeconds());
    }

    /** Revokes the access token from the Authorization header and, if supplied, the refresh token. */
    @Transactional
    public void logout(String accessToken, LogoutRequest request) {
        boolean revokedAny = false;
        if (accessToken != null) {
            Claims claims = parse(accessToken);
            if (tokenProvider.isAccessToken(claims)) {
                revoke(claims);
                revokedAny = true;
            }
        }
        if (request != null && request.refreshToken() != null && !request.refreshToken().isBlank()) {
            Claims claims = parse(request.refreshToken());
            if (tokenProvider.isRefreshToken(claims)) {
                revoke(claims);
                revokedAny = true;
            }
        }
        if (!revokedAny) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "No valid token supplied");
        }
    }

    @Scheduled(cron = "0 15 3 * * *")
    @Transactional
    public void purgeExpiredRevocations() {
        int removed = revokedTokenRepository.deleteExpired(Instant.now());
        log.info("Purged expired revoked tokens | count={}", removed);
    }

    private void revoke(Claims claims) {
        String tokenId = tokenProvider.getTokenId(claims);
        if (!revokedTokenRepository.existsById(tokenId)) {
            revokedTokenRepository.save(new RevokedToken(tokenId, tokenProvider.getExpiration(claims)));
        }
    }

    private Claims parse(String token) {
        try {
            return tokenProvider.parseToken(token);
        } catch (RuntimeException exception) {
            throw ApiException.unauthorized("Invalid or expired token");
        }
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
