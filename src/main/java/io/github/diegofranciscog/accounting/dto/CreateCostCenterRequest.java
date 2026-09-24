package io.github.diegofranciscog.accounting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateCostCenterRequest(
        @NotBlank @Pattern(regexp = "^[A-Z0-9-]{1,20}$", message = "A-Z, 0-9 y guion; máximo 20") String code,
        @NotBlank @Size(max = 120) String name) {
}
