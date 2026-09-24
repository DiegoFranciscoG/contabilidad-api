package io.github.diegofranciscog.accounting.dto.report;

import io.github.diegofranciscog.accounting.dto.JournalEntryResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Libro diario: posted entries in chronological order. */
public record JournalReport(LocalDate from, LocalDate to, List<JournalEntryResponse> entries, BigDecimal totalDebit,
                            BigDecimal totalCredit) {
}
