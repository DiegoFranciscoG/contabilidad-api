package io.github.diegofranciscog.accounting.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Optional first users and demo data. Nothing is created when the values are empty,
 * so there is no default password anywhere.
 */
@ConfigurationProperties(prefix = "app.bootstrap")
public record BootstrapProperties(String adminEmail, String adminPassword, boolean demoData, String demoPassword) {

    public boolean hasAdmin() {
        return hasText(adminEmail) && hasText(adminPassword);
    }

    public boolean hasDemoPassword() {
        return hasText(demoPassword);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank() && !value.contains("${");
    }
}
