package io.github.diegofranciscog.accounting.dto;

import io.github.diegofranciscog.accounting.domain.Nature;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** When {@code parentCode} is empty the parent is the longest existing code that prefixes {@code code}. */
public record CreateAccountRequest(
        @NotBlank @Pattern(regexp = "^[1-5][0-9]{0,19}$", message = "solo dígitos, empieza en 1-5, máximo 20") String code,
        @NotBlank @Size(max = 200) String name,
        @Pattern(regexp = "^[1-5][0-9]{0,19}$", message = "solo dígitos, empieza en 1-5, máximo 20") String parentCode,
        Nature nature,
        @NotNull Boolean acceptsEntries) {
}
