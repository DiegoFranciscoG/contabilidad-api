package io.github.diegofranciscog.accounting.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.diegofranciscog.accounting.config.SecurityProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;

/**
 * Limits login attempts per client IP and per e-mail (OWASP API4). Buckets live in a bounded cache,
 * so an attacker rotating keys cannot exhaust memory.
 */
@Component
public class LoginRateLimiter {

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(Duration.ofMinutes(10))
            .build();
    private final int attemptsPerMinute;

    public LoginRateLimiter(SecurityProperties properties) {
        this.attemptsPerMinute = properties.loginAttemptsPerMinute();
    }

    /** Consumes one attempt for both keys; false means the caller must answer 429. */
    public boolean tryAcquire(String clientIp, String email) {
        boolean ipAllowed = bucket("ip:" + clientIp).tryConsume(1);
        boolean emailAllowed = bucket("email:" + email.strip().toLowerCase(Locale.ROOT)).tryConsume(1);
        return ipAllowed && emailAllowed;
    }

    private Bucket bucket(String key) {
        return buckets.get(key, ignored -> Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(attemptsPerMinute).refillGreedy(attemptsPerMinute, Duration.ofMinutes(1)).build())
                .build());
    }
}
