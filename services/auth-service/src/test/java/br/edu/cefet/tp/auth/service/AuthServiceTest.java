package br.edu.cefet.tp.auth.service;

import br.edu.cefet.tp.auth.dto.AuthResponse;
import br.edu.cefet.tp.auth.dto.LoginRequest;
import br.edu.cefet.tp.auth.dto.RegisterRequest;
import br.edu.cefet.tp.auth.exception.EmailAlreadyExistsException;
import br.edu.cefet.tp.auth.exception.InvalidPasswordException;
import br.edu.cefet.tp.auth.model.User;
import br.edu.cefet.tp.auth.repository.UserRepository;
import br.edu.cefet.tp.auth.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService);
    }

    @Test
    void registerNormalizesEmailAndStoresOnlyPasswordHash() {
        when(userRepository.existsByEmailIgnoreCase("user@example.com")).thenReturn(false);
        when(passwordEncoder.encode("strong-password")).thenReturn("bcrypt-hash");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = authService.register(
                new RegisterRequest("  Example User ", "USER@Example.com", "strong-password")
        );

        assertEquals("Example User", result.displayName());
        assertEquals("user@example.com", result.email());
        verify(passwordEncoder).encode("strong-password");
        verify(userRepository).saveAndFlush(any(User.class));
    }

    @Test
    void registerRejectsAnExistingEmail() {
        when(userRepository.existsByEmailIgnoreCase("user@example.com")).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class, () -> authService.register(
                new RegisterRequest("Example User", "user@example.com", "strong-password")
        ));

        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void registerRejectsPasswordsLongerThanBcryptByteLimit() {
        assertThrows(InvalidPasswordException.class, () -> authService.register(
                new RegisterRequest("Example User", "user@example.com", "é".repeat(37))
        ));

        verify(userRepository, never()).existsByEmailIgnoreCase(any());
    }

    @Test
    void loginIssuesBearerTokenForValidCredentials() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(UUID.randomUUID());
        when(user.getDisplayName()).thenReturn("Example User");
        when(user.getEmail()).thenReturn("user@example.com");
        when(user.getPasswordHash()).thenReturn("bcrypt-hash");
        when(user.getCreatedAt()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("strong-password", "bcrypt-hash")).thenReturn(true);
        when(jwtService.issueToken(user)).thenReturn("signed-token");
        when(jwtService.getExpirationSeconds()).thenReturn(3600L);

        AuthResponse result = authService.login(
                new LoginRequest(" USER@Example.com ", "strong-password")
        );

        assertEquals("signed-token", result.accessToken());
        assertEquals("Bearer", result.tokenType());
        assertEquals(3600L, result.expiresIn());
        assertEquals(user.getId(), result.user().id());
    }

    @Test
    void loginRejectsInvalidPassword() {
        User user = mock(User.class);
        when(user.getPasswordHash()).thenReturn("bcrypt-hash");
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "bcrypt-hash")).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> authService.login(
                new LoginRequest("user@example.com", "wrong-password")
        ));

        verify(jwtService, never()).issueToken(any(User.class));
    }

    @Test
    void loginRejectsUnknownEmail() {
        when(userRepository.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class, () -> authService.login(
                new LoginRequest("missing@example.com", "strong-password")
        ));
    }
}