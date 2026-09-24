package io.github.diegofranciscog.accounting;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Full application against PostgreSQL in Testcontainers. Secrets are random per run, so no secret value is ever
 * written in the repository. Test classes share one context and one container, so each class uses its own years;
 * ReportServiceIT owns the earliest one (2030) because cumulative balances include every earlier year.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIntegrationTest {

    public static final String JWT_SECRET = randomSecret();
    public static final String HMAC_SECRET = randomSecret();

    @DynamicPropertySource
    static void secrets(DynamicPropertyRegistry registry) {
        registry.add("app.security.jwt-secret", () -> JWT_SECRET);
        registry.add("app.security.cors-allowed-origins", () -> "http://localhost:4200");
        registry.add("app.integration.hmac-secret", () -> HMAC_SECRET);
    }

    private static String randomSecret() {
        byte[] bytes = new byte[48];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
