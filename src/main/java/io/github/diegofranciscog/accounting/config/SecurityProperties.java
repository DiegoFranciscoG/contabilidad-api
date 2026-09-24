package io.github.diegofranciscog.accounting.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/** Security settings. Secrets come only from environment variables and have no default value. */
@Validated
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        @NotNull String jwtSecret,
        @NotNull Duration jwtTtl,
        @NotEmpty List<String> corsAllowedOrigins,
        @Min(1) @Max(100) int loginAttemptsPerMinute,
        boolean publicApiDocs) {

    /** HS256 needs a key of at least 256 bits. */
    public static final int MIN_SECRET_BYTES = 32;

    public SecurityProperties {
        requireSecret("JWT_SECRET", jwtSecret);
        if (jwtTtl == null || jwtTtl.isNegative() || jwtTtl.isZero() || jwtTtl.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalStateException("JWT_TTL debe estar entre 1 segundo y 1 hora");
        }
        if (corsAllowedOrigins == null || corsAllowedOrigins.isEmpty()
                || corsAllowedOrigins.stream().anyMatch(origin -> origin.isBlank() || origin.contains("*") || origin.contains("${"))) {
            throw new IllegalStateException("Define CORS_ALLOWED_ORIGINS con orígenes explícitos separados por coma (sin *)");
        }
    }

    static void requireSecret(String variable, String value) {
        if (value == null || value.isBlank() || value.contains("${")) {
            throw new IllegalStateException("Define la variable de entorno " + variable);
        }
        if (value.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(variable + " debe tener al menos " + MIN_SECRET_BYTES + " bytes (usa: openssl rand -base64 48)");
        }
    }
}
