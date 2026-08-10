package com.start.overflow.order.entity;

import com.start.overflow.shared.exception.ValidationException;

import java.math.BigDecimal;

public record OrderProductSnapshot(
        Long productId,
        String productName,
        String sku,
        BigDecimal unitPrice
) {
    public OrderProductSnapshot {
        if (productId == null || productName == null || productName.isBlank()
                || sku == null || sku.isBlank() || unitPrice == null) {
            throw new ValidationException("O snapshot do produto é obrigatório");
        }
        if (unitPrice.signum() < 0) {
            throw new ValidationException("O preço do snapshot não pode ser negativo");
        }
        productName = productName.strip();
        sku = sku.strip();
    }
}
