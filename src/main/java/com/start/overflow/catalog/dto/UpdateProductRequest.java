package com.start.overflow.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateProductRequest(
        @NotNull(message = "A categoria é obrigatória") Long categoryId,
        @NotBlank(message = "O nome é obrigatório") @Size(max = 120) String name,
        @Size(max = 2000, message = "A descrição deve ter no máximo 2000 caracteres") String description,
        @NotNull(message = "O preço é obrigatório") @Positive(message = "O preço deve ser positivo") BigDecimal price
) {
}
