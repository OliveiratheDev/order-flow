package com.start.overflow.identity.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "orderflow.bootstrap.admin")
public record AdminBootstrapProperties(
        boolean enabled,
        String name,
        String email,
        String password
) {
}
