package io.github.diegofranciscog.accounting.dto;

import io.github.diegofranciscog.accounting.domain.UserRole;

import java.time.Instant;

public record UserResponse(Long id, String email, String fullName, UserRole role, boolean enabled, Instant createdAt) {
}
