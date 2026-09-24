package io.github.diegofranciscog.accounting.dto;

import io.github.diegofranciscog.accounting.domain.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 12, max = 128) String password,
        @NotBlank @Size(max = 120) String fullName,
        @NotNull UserRole role) {

    @Override
    public String toString() {
        return "CreateUserRequest[email=" + email + ", role=" + role + ", password=***]";
    }
}
