package com.start.overflow.catalog.dto;

import java.io.Serializable;

public record CategorySummaryResponse(
        Long id,
        String name,
        String slug
) implements Serializable {
}
