package com.start.overflow.catalog.dto;

import com.start.overflow.shared.exception.ValidationException;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductFilter(
        @Size(max = 120) String name,
        @Positive Long categoryId,
        @PositiveOrZero BigDecimal minPrice,
        @PositiveOrZero BigDecimal maxPrice,
        Boolean active
) {
    public void validatePriceRange() {
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new ValidationException("A faixa de preço informada é inválida");
        }
    }
}
