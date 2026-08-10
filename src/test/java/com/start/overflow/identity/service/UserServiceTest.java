package com.start.overflow.identity.service;

import com.start.overflow.identity.dto.RegisterRequest;
import com.start.overflow.identity.entity.AppUser;
import com.start.overflow.identity.mapper.UserMapper;
import com.start.overflow.identity.repository.UserRepository;
import com.start.overflow.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock UserRepository repository;
    @Mock PasswordEncoder encoder;
    @Mock UserMapper mapper;
    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(repository, encoder, mapper);
    }

    @Test
    void hashesPasswordBeforePersistence() {
        RegisterRequest request = new RegisterRequest("Maria", "MARIA@example.com",
                "529.982.247-25", "Senha123!");
        when(encoder.encode("Senha123!")).thenReturn("$2a$12$encoded");
        when(repository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.register(request);

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("maria@example.com");
        assertThat(captor.getValue().getDocument()).isEqualTo("52998224725");
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("$2a$12$encoded");
        assertThat(captor.getValue().getPasswordHash()).doesNotContain("Senha123!");
    }

    @Test
    void refusesDuplicateEmailBeforeHashing() {
        when(repository.existsByEmailIgnoreCase("maria@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(
                new RegisterRequest("Maria", "maria@example.com", "52998224725", "Senha123!")))
                .isInstanceOf(BusinessRuleException.class);
        verify(encoder, never()).encode(any());
    }

    @Test
    void refusesDuplicateDocumentBeforeHashing() {
        when(repository.existsByDocument("52998224725")).thenReturn(true);

        assertThatThrownBy(() -> service.register(
                new RegisterRequest("Maria", "maria@example.com", "529.982.247-25",
                        "Senha123!")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("CPF ou CNPJ");
        verify(encoder, never()).encode(any());
    }
}
