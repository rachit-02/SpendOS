package com.spendos.auth.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.spendos.auth.dto.LoginRequest;
import com.spendos.auth.dto.RegisterRequest;
import com.spendos.auth.domain.User;
import com.spendos.auth.repository.RevokedTokenRepository;
import com.spendos.auth.repository.UserRepository;
import com.spendos.common.exception.ApiException;
import com.spendos.common.security.JwtTokenProvider;
import com.spendos.users.service.UserPreferencesService;
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

    @Mock
    private UserPreferencesService preferencesService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, new BCryptPasswordEncoder(4), tokenProvider,
                revokedTokenRepository, new PasswordPolicy(), preferencesService);
    }

    @Test
    void rejectsDuplicateEmailBeforeSaving() {
        when(userRepository.existsByEmailIgnoreCase("user@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("User@example.com", "Tr0pic@lThund3r!", "User")))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("DUPLICATE_EMAIL");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void rejectsWeakPasswordBeforeSaving() {
        assertThatThrownBy(() -> authService.register(new RegisterRequest("a@example.com", "short", "User")))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("WEAK_PASSWORD");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void rejectsInvalidCredentials() {
        User user = new User("user@example.com", new BCryptPasswordEncoder(4).encode("Tr0pic@lThund3r!"), "User");
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("user@example.com"))
                .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("user@example.com", "WrongPassword1!")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void unknownEmailGivesSameErrorAsWrongPassword() {
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost@example.com", "Tr0pic@lThund3r!")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid email or password");
    }
}
