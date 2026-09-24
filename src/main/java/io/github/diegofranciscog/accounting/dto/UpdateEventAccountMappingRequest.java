package io.github.diegofranciscog.accounting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateEventAccountMappingRequest(@NotBlank @Pattern(regexp = "^[1-5][0-9]{0,19}$") String accountCode) {
}
