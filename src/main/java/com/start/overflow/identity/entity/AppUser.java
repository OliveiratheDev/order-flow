package com.start.overflow.identity.entity;

import com.start.overflow.shared.exception.ValidationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import org.hibernate.annotations.BatchSize;

import java.time.Instant;
import java.util.Locale;

@Entity
@Getter
@Table(name = "app_user")
@BatchSize(size = 50)
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 160, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 120)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Column(nullable = false)
    private Boolean active;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected AppUser() {
    }

    public AppUser(String name, String email, String passwordHash, UserRole role) {
        this.name = normalizeName(name);
        this.email = normalizeEmail(email);
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new ValidationException("A senha criptografada é obrigatória");
        }
        if (role == null) {
            throw new ValidationException("O perfil do usuário é obrigatório");
        }
        this.passwordHash = passwordHash;
        this.role = role;
        this.active = true;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    private void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public static String normalizeEmail(String value) {
        if (value == null || value.isBlank()) {
            throw new ValidationException("O e-mail é obrigatório");
        }
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        if (normalized.length() > 160) {
            throw new ValidationException("O e-mail deve ter no máximo 160 caracteres");
        }
        return normalized;
    }

    private static String normalizeName(String value) {
        if (value == null || value.isBlank()) {
            throw new ValidationException("O nome é obrigatório");
        }
        String normalized = value.strip();
        if (normalized.length() > 120) {
            throw new ValidationException("O nome deve ter no máximo 120 caracteres");
        }
        return normalized;
    }
}
