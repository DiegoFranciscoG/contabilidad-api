package io.github.diegofranciscog.accounting.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** One side only: fill {@code debit} or {@code credit}; the other may be omitted or zero. */
public record JournalLineRequest(
        @NotBlank @Pattern(regexp = "^[1-5][0-9]{0,19}$") String accountCode,
        @Pattern(regexp = "^[A-Z0-9-]{1,20}$") String costCenterCode,
        @DecimalMin("0.00") @Digits(integer = 16, fraction = 2) BigDecimal debit,
        @DecimalMin("0.00") @Digits(integer = 16, fraction = 2) BigDecimal credit,
        @Size(max = 250) String description) {
}
