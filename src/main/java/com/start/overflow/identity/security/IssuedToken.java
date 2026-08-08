package com.start.overflow.identity.security;

import java.time.Instant;

public record IssuedToken(String value, Instant expiresAt) {
}
