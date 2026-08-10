package com.start.overflow.identity.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "orderflow.security.jwt")
public record JwtProperties(String issuer, Duration expiration, String secret) {
}
