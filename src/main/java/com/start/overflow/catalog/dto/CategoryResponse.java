package com.start.overflow.catalog.dto;


import java.time.LocalDateTime;

public record CategoryResponse(
        Long id,
        String name,
        String slug,
        String description,
        Boolean active,
        LocalDateTime createAt234
) {

}
