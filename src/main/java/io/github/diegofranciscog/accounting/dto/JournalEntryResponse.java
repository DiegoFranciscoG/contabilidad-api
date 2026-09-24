package io.github.diegofranciscog.accounting.dto;

import io.github.diegofranciscog.accounting.domain.EntrySource;
import io.github.diegofranciscog.accounting.domain.EntryStatus;
import io.github.diegofranciscog.accounting.domain.EntryType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** {@code number} is "year-sequence" (for example 2026-15) and is null for drafts. */
public record JournalEntryResponse(Long id, String number, LocalDate date, String period, String description,
                                   EntryStatus status, EntryType type, EntrySource source, String sourceReference,
                                   Long reversesEntryId, Long reversedByEntryId, BigDecimal totalDebit,
                                   BigDecimal totalCredit, String createdBy, Instant createdAt, Instant postedAt,
                                   List<JournalLineResponse> lines) {
}
