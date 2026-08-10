package com.start.overflow.identity.dto;

import com.start.overflow.identity.entity.UserRole;

import java.time.Instant;

public record UserResponse(
        Long id,
        String name,
        String email,
        UserRole role,
        Boolean active,
        Instant createdAt
) {
}
