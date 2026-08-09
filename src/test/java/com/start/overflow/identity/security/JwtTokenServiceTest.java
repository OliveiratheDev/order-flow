package com.start.overflow.identity.security;

import com.start.overflow.identity.entity.AppUser;
import com.start.overflow.identity.entity.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTest {
    private static final String SECRET = "a-test-secret-with-more-than-thirty-two-bytes";
    private JwtTokenService service;
    private JwtDecoder decoder;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties("orderflow", Duration.ofMinutes(15), SECRET);
        SecurityConfig config = new SecurityConfig();
        SecretKey key = config.jwtSecretKey(properties);
        service = new JwtTokenService(config.jwtEncoder(key), properties);
        decoder = config.jwtDecoder(key, properties);
    }

    @Test
    void issuesSignedTokenWithRequiredClaims() {
        AppUser user = new AppUser("Maria", "maria@example.com", "$2a$12$hash", UserRole.ADMIN);
        ReflectionTestUtils.setField(user, "id", 42L);

        IssuedToken issued = service.issue(user);
        Jwt jwt = decoder.decode(issued.value());

        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("orderflow");
        assertThat(jwt.getId()).isNotBlank();
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ADMIN");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).toMinutes()).isEqualTo(15);
    }

    @Test
    void rejectsTamperedSignature() {
        AppUser user = new AppUser("Maria", "maria@example.com", "52998224725",
                "$2a$12$hash", UserRole.CUSTOMER);
        ReflectionTestUtils.setField(user, "id", 1L);
        String token = service.issue(user).value();
        String[] segments = token.split("\\.");
        char replacement = segments[2].charAt(0) == 'a' ? 'b' : 'a';
        String tamperedSignature = replacement + segments[2].substring(1);
        String tampered = String.join(".", segments[0], segments[1], tamperedSignature);

        assertThatThrownBy(() -> decoder.decode(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsShortSecret() {
        SecurityConfig config = new SecurityConfig();
        JwtProperties properties = new JwtProperties("orderflow", Duration.ofMinutes(15), "short");

        assertThatThrownBy(() -> config.jwtSecretKey(properties))
                .isInstanceOf(IllegalStateException.class);
    }
}
