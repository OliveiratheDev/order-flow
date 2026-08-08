package com.start.overflow.catalog.entity;

import com.start.overflow.shared.exception.BusinessRuleException;
import com.start.overflow.shared.exception.ValidationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Locale;

@Entity
@Getter
@Table(name = "product")
@BatchSize(size = 50)
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 40, unique = true, updatable = false)
    private String sku;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer stock;

    @Column(nullable = false)
    private Boolean active;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected Product() {
    }

    public Product(Category category, String name, String sku, String description,
                   BigDecimal price, int stock) {
        changeCategory(category);
        this.name = normalizeName(name);
        this.sku = normalizeSku(sku);
        this.description = normalizeDescription(description);
        this.price = normalizePrice(price);
        if (stock < 0) {
            throw new ValidationException("O estoque inicial não pode ser negativo");
        }
        this.stock = stock;
        this.active = true;
    }

    public void update(Category category, String name, String description, BigDecimal price) {
        changeCategory(category);
        this.name = normalizeName(name);
        this.description = normalizeDescription(description);
        this.price = normalizePrice(price);
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public void adjustStock(int quantity) {
        if (quantity == 0) {
            throw new ValidationException("O ajuste de estoque não pode ser zero");
        }
        int adjustedStock;
        try {
            adjustedStock = Math.addExact(this.stock, quantity);
        } catch (ArithmeticException exception) {
            throw new BusinessRuleException("O ajuste de estoque excede o limite permitido");
        }
        if (adjustedStock < 0) {
            throw new BusinessRuleException("O ajuste deixaria o estoque negativo");
        }
        this.stock = adjustedStock;
    }

    public void reserveStock(int quantity) {
        if (!this.active) {
            throw new BusinessRuleException("O produto " + this.sku + " está inativo");
        }
        if (quantity <= 0) {
            throw new ValidationException("A quantidade solicitada deve ser positiva");
        }
        if (this.stock < quantity) {
            throw new BusinessRuleException("Estoque insuficiente para o produto " + this.sku);
        }
        this.stock -= quantity;
    }

    public void restoreStock(int quantity) {
        if (quantity <= 0) {
            throw new ValidationException("A quantidade devolvida deve ser positiva");
        }
        this.stock = Math.addExact(this.stock, quantity);
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

    private void changeCategory(Category category) {
        if (category == null) {
            throw new ValidationException("A categoria é obrigatória");
        }
        if (!Boolean.TRUE.equals(category.getActive())) {
            throw new BusinessRuleException("Não é possível associar um produto a uma categoria inativa");
        }
        this.category = category;
    }

    private static String normalizeName(String value) {
        if (value == null || value.isBlank()) {
            throw new ValidationException("O nome do produto é obrigatório");
        }
        String normalized = value.strip();
        if (normalized.length() > 120) {
            throw new ValidationException("O nome do produto deve ter no máximo 120 caracteres");
        }
        return normalized;
    }

    private static String normalizeSku(String value) {
        if (value == null || value.isBlank()) {
            throw new ValidationException("O SKU é obrigatório");
        }
        String normalized = value.strip().toUpperCase(Locale.ROOT);
        if (normalized.length() > 40) {
            throw new ValidationException("O SKU deve ter no máximo 40 caracteres");
        }
        return normalized;
    }

    private static String normalizeDescription(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static BigDecimal normalizePrice(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new ValidationException("O preço deve ser positivo");
        }
        BigDecimal normalized = value.setScale(2, RoundingMode.HALF_UP);
        if (normalized.precision() > 12) {
            throw new ValidationException("O preço excede o limite permitido");
        }
        return normalized;
    }
}
