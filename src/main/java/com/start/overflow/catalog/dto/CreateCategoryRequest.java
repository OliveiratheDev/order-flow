package com.start.overflow.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCategoryRequest(@NotBlank @Size(max = 80) String name,
                                    @Size(max = 255) String description) {
}

