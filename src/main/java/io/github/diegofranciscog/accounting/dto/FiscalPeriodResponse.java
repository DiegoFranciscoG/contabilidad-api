package io.github.diegofranciscog.accounting.dto;

import io.github.diegofranciscog.accounting.domain.PeriodStatus;

import java.time.Instant;
import java.time.LocalDate;

public record FiscalPeriodResponse(Long id, int year, int month, LocalDate startDate, LocalDate endDate,
                                   PeriodStatus status, Instant closedAt, String closedBy) {
}
