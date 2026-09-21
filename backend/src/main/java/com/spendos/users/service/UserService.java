package com.spendos.users.service;

import com.spendos.audit.service.AuditService;
import com.spendos.auth.dto.UserResponse;
import com.spendos.auth.domain.User;
import com.spendos.auth.repository.UserRepository;
import com.spendos.auth.service.PasswordPolicy;
import com.spendos.common.exception.ApiException;
import com.spendos.common.util.Times;
import com.spendos.users.dto.UserDtos.ChangePasswordRequest;
import com.spendos.users.dto.UserDtos.UpdateProfileRequest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final AuditService auditService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(UUID userId) {
        return UserResponse.from(activeUser(userId));
    }

    @Transactional
    public UserResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = activeUser(userId);
        Map<String, Object> before = profile(user);
        if (request.fullName() != null) {
            String fullName = request.fullName().trim();
            if (fullName.isEmpty()) {
                throw ApiException.badRequest("INVALID_REQUEST", "fullName must not be blank");
            }
            user.setFullName(fullName);
        }
        if (request.email() != null) {
            String email = request.email().trim().toLowerCase(Locale.ROOT);
            if (!email.equals(user.getEmail())) {
                if (userRepository.existsByEmailIgnoreCaseAndIdNot(email, userId)) {
                    throw ApiException.badRequest("DUPLICATE_EMAIL", "Email already in use");
                }
                user.setEmail(email);
            }
        }
        User saved = userRepository.saveAndFlush(user);
        // Right to rectification (SECURITY.md): profile changes are audited with their old values.
        auditService.record(userId, "user", userId, AuditService.UPDATE, before, profile(saved));
        log.info("Profile updated | userId={} | action=update | entity=user", userId);
        return UserResponse.from(saved);
    }

    /** Changes the password and invalidates every token issued before now. */
    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = activeUser(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ApiException.unauthorized("Current password incorrect");
        }
        passwordPolicy.validate(request.newPassword(), user.getEmail());
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("WEAK_PASSWORD", "New password must differ from the current password");
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()), Instant.now().truncatedTo(ChronoUnit.SECONDS));
        userRepository.save(user);
        auditService.record(userId, "user", userId, AuditService.UPDATE, null, Map.of("passwordChanged", true));
        log.info("Password changed | userId={} | action=change_password", userId);
    }

    /** Soft-deletes the account; data is retained 30 days for recovery before purge. */
    @Transactional
    public void deleteAccount(UUID userId, String confirmPassword) {
        User user = activeUser(userId);
        if (!passwordEncoder.matches(confirmPassword, user.getPasswordHash())) {
            throw ApiException.unauthorized("Confirmation password incorrect");
        }
        user.softDelete(Times.nowUtc(), Instant.now().truncatedTo(ChronoUnit.SECONDS));
        userRepository.save(user);
        auditService.record(userId, "user", userId, AuditService.DELETE, null, Map.of("purgeAfterDays", 30));
        log.info("Account soft-deleted | userId={} | action=delete | entity=user", userId);
    }

    private static Map<String, Object> profile(User user) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("fullName", user.getFullName());
        values.put("email", user.getEmail());
        return values;
    }

    private User activeUser(UUID userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .filter(User::isActive)
                .orElseThrow(() -> ApiException.unauthorized("User not found or inactive"));
    }
}
