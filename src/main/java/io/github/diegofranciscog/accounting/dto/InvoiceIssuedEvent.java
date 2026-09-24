package io.github.diegofranciscog.accounting.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Sent by facturador-sri when an invoice is authorised. Amounts arrive already computed (S6);
 * no customer identification is received (LOPDP data minimisation).
 */
public record InvoiceIssuedEvent(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9-]{1,60}$") String invoiceId,
        @NotNull LocalDate issueDate,
        @NotNull RevenueKind kind,
        @NotNull @DecimalMin("0.01") @Digits(integer = 16, fraction = 2) BigDecimal subtotal,
        @NotNull @DecimalMin("0.00") @Digits(integer = 16, fraction = 2) BigDecimal vat,
        @NotNull @DecimalMin("0.01") @Digits(integer = 16, fraction = 2) BigDecimal total,
        @Pattern(regexp = "^[A-Z0-9-]{1,20}$") String costCenterCode) {
}
