package com.start.overflow.catalog.entity;

import com.start.overflow.shared.exception.ValidationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;

import java.text.Normalizer;
import java.time.Instant;
import java.util.Locale;

@Entity
@Getter
@Table(name = "category")
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 80)
    private String name;
    @Column(nullable = false, length = 80, unique = true)
    private String slug;
    @Column(length = 255)
    private String description;
    @Column(nullable = false)
    private Boolean active;
    @Version
    @Column(nullable = false)
    private Long version;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;

    protected Category() {
    }

    public Category(String name, String description) {
        this.name = normalizeName(name);
        this.description = normalizeDescription(description);
        this.slug = generateSlug(this.name);
        this.active = true;
    }

    public static String generateSlug(String text) {
        String normalizedName = normalizeName(text);
        String withoutAccents = Normalizer.normalize(normalizedName, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String result = withoutAccents.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (result.isBlank()) {
            throw new ValidationException("O nome deve conter ao menos uma letra ou número");
        }
        return result;
    }

    public void activate() {
        this.active = true;
    }
    public void deactivate() {
        this.active = false;
    }

    public void rename(String newName) {
        this.name = normalizeName(newName);
        this.slug = generateSlug(this.name);
    }

    public void updateDescription(String newDescription) {
        this.description = normalizeDescription(newDescription);
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

    private static String normalizeName(String value) {
        if (value == null || value.isBlank()) {
            throw new ValidationException("O nome da categoria é obrigatório");
        }
        String normalized = value.strip();
        if (normalized.length() > 80) {
            throw new ValidationException("O nome da categoria deve ter no máximo 80 caracteres");
        }
        return normalized;
    }

    private static String normalizeDescription(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.length() > 255) {
            throw new ValidationException("A descrição deve ter no máximo 255 caracteres");
        }
        return normalized;
    }
}
