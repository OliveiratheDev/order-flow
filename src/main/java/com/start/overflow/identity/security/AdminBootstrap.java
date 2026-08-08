package com.start.overflow.identity.security;

import com.start.overflow.identity.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "orderflow.bootstrap.admin", name = "enabled", havingValue = "true")
public class AdminBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserService userService;
    private final AdminBootstrapProperties properties;

    public AdminBootstrap(UserService userService, AdminBootstrapProperties properties) {
        this.userService = userService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        userService.createAdminIfMissing(properties.name(), properties.email(), properties.password());
        log.info("Local administrator bootstrap verified");
    }
}
