package io.github.diegofranciscog.accounting.dto;

import io.github.diegofranciscog.accounting.domain.EntrySource;
import io.github.diegofranciscog.accounting.domain.EntryStatus;
import io.github.diegofranciscog.accounting.domain.EntryType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record JournalEntrySummary(Long id, String number, LocalDate date, String description, EntryStatus status,
                                  EntryType type, EntrySource source, BigDecimal total) {
}
