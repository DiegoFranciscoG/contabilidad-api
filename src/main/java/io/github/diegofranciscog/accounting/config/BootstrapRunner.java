package io.github.diegofranciscog.accounting.config;

import io.github.diegofranciscog.accounting.domain.UserRole;
import io.github.diegofranciscog.accounting.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Creates the first administrator from ADMIN_EMAIL / ADMIN_PASSWORD when both are set. */
@Component
@Order(1)
public class BootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapRunner.class);
    private static final int MIN_PASSWORD = 12;

    private final BootstrapProperties properties;
    private final UserService users;

    public BootstrapRunner(BootstrapProperties properties, UserService users) {
        this.properties = properties;
        this.users = users;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.hasAdmin()) {
            return;
        }
        if (properties.adminPassword().length() < MIN_PASSWORD) {
            throw new IllegalStateException("ADMIN_PASSWORD debe tener al menos " + MIN_PASSWORD + " caracteres");
        }
        if (users.createIfMissing(properties.adminEmail(), properties.adminPassword(), "Administrador", UserRole.ADMIN)) {
            log.info("Usuario administrador inicial creado");
        }
    }
}
