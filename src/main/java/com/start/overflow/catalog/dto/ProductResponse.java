package com.start.overflow.catalog.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
        Long id,
        String name,
        String sku,
        String description,
        BigDecimal price,
        Integer stock,
        Boolean active,
        CategorySummaryResponse category,
        Instant createdAt,
        Instant updatedAt
) implements Serializable {
}
