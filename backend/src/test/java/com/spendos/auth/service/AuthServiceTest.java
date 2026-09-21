package com.spendos.auth.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.spendos.auth.dto.LoginRequest;
import com.spendos.auth.dto.RegisterRequest;
import com.spendos.auth.entity.User;
import com.spendos.auth.repository.RevokedTokenRepository;
import com.spendos.auth.repository.UserRepository;
import com.spendos.common.security.JwtTokenProvider;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private RevokedTokenRepository revokedTokenRepository;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                new BCryptPasswordEncoder(4),
                tokenProvider,
                revokedTokenRepository);
    }

    @Test
    void rejectsDuplicateEmailBeforeSaving() {
        when(userRepository.existsByEmailIgnoreCase("user@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("User@example.com", "StrongPassword1!", "User")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Email is already registered");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void rejectsInvalidCredentials() {
        User user = org.mockito.Mockito.mock(User.class);
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("user@example.com"))
                .thenReturn(Optional.of(user));
        when(user.isActive()).thenReturn(true);
        when(user.getPasswordHash()).thenReturn(new BCryptPasswordEncoder(4).encode("StrongPassword1!"));

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("user@example.com", "WrongPassword1!")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid email or password");
    }
}
