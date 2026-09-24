package io.github.diegofranciscog.accounting.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Sent by nomina-ec when a payroll run is approved. Totals only, never per-employee data (LOPDP).
 * IESS contributions are computed by nomina-ec (S6).
 */
public record PayrollAccruedEvent(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9-]{1,60}$") String payrollId,
        @NotNull LocalDate accrualDate,
        @NotNull @DecimalMin("0.01") @Digits(integer = 16, fraction = 2) BigDecimal grossSalaries,
        @NotNull @DecimalMin("0.00") @Digits(integer = 16, fraction = 2) BigDecimal employeeContribution,
        @NotNull @DecimalMin("0.00") @Digits(integer = 16, fraction = 2) BigDecimal employerContribution,
        @NotNull @DecimalMin("0.00") @Digits(integer = 16, fraction = 2) BigDecimal benefitsProvision,
        @Pattern(regexp = "^[A-Z0-9-]{1,20}$") String costCenterCode) {
}
