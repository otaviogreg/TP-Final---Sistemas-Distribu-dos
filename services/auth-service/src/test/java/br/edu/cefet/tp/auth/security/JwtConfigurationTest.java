package br.edu.cefet.tp.auth.security;

import br.edu.cefet.tp.auth.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtConfigurationTest {

    @Test
    void issuedTokenCanBeValidatedWithConfiguredIssuerAndKey() {
        String secret = Base64.getEncoder().encodeToString(new byte[32]);
        JwtConfiguration configuration = new JwtConfiguration();
        var key = configuration.jwtSecretKey(secret);
        JwtService jwtService = new JwtService(
                configuration.jwtEncoder(key),
                "test-auth-service",
                300
        );
        User user = mock(User.class);
        when(user.getId()).thenReturn(UUID.fromString("1cda8c19-1588-4963-95ac-b93168d3ab9c"));
        when(user.getEmail()).thenReturn("ana@example.com");
        when(user.getDisplayName()).thenReturn("Ana Silva");

        String token = jwtService.issueToken(user);
        Jwt decoded = configuration.jwtDecoder(key, "test-auth-service").decode(token);

        assertEquals("test-auth-service", decoded.getClaimAsString("iss"));
        assertEquals("1cda8c19-1588-4963-95ac-b93168d3ab9c", decoded.getSubject());
        assertEquals("ana@example.com", decoded.getClaimAsString("email"));
    }
}
