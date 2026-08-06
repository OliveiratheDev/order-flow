package com.start.overflow.catalog.dto;


import java.time.LocalDateTime;
import java.util.List;

public record PagedResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totaPages
) {}

