package com.start.overflow.catalog.dto;


import java.io.Serializable;
import java.time.Instant;

public record CategoryResponse(
        Long id,
        String name,
        String slug,
        String description,
        Boolean active,
        Instant createdAt,
        Instant updatedAt
) implements Serializable {
}
