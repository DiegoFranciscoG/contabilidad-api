package io.github.diegofranciscog.accounting.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Shared secret used by facturador-sri and nomina-ec to sign their events (HMAC-SHA256). */
@Validated
@ConfigurationProperties(prefix = "app.integration")
public record IntegrationProperties(@NotNull String hmacSecret, @NotNull Duration maxClockSkew) {

    public IntegrationProperties {
        SecurityProperties.requireSecret("INTEGRATION_HMAC_SECRET", hmacSecret);
    }
}
