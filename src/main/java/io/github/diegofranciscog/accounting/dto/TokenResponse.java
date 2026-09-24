package io.github.diegofranciscog.accounting.dto;

import io.github.diegofranciscog.accounting.domain.UserRole;

public record TokenResponse(String accessToken, String tokenType, long expiresIn, UserRole role) {
}
