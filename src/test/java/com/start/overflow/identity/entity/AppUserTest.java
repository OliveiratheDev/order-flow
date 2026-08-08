package com.start.overflow.identity.entity;

import com.start.overflow.shared.exception.ValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppUserTest {
    @Test
    void normalizesIdentityDataWithoutExposingPassword() {
        AppUser user = new AppUser("  Maria Silva  ", "  MARIA@EXAMPLE.COM ",
                "$2a$12$hash", UserRole.CUSTOMER);

        assertThat(user.getName()).isEqualTo("Maria Silva");
        assertThat(user.getEmail()).isEqualTo("maria@example.com");
        assertThat(user.getPasswordHash()).isNotEqualTo("senha");
        assertThat(user.getActive()).isTrue();
    }

    @Test
    void rejectsMissingPasswordHash() {
        assertThatThrownBy(() -> new AppUser("Maria", "maria@example.com", " ",
                UserRole.CUSTOMER)).isInstanceOf(ValidationException.class);
    }
}
