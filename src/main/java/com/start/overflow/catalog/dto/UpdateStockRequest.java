package com.start.overflow.catalog.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateStockRequest(
        @NotNull(message = "O ajuste de estoque é obrigatório") Integer quantity
) {
}
