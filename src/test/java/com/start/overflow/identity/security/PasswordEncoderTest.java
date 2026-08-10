package com.start.overflow.identity.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordEncoderTest {
    @Test
    void bcryptNeverStoresRawPassword() {
        PasswordEncoder encoder = new SecurityConfig().passwordEncoder();

        String encoded = encoder.encode("Senha123!");

        assertThat(encoded).startsWith("$2");
        assertThat(encoded).doesNotContain("Senha123!");
        assertThat(encoder.matches("Senha123!", encoded)).isTrue();
        assertThat(encoder.matches("senha-errada", encoded)).isFalse();
    }
}
