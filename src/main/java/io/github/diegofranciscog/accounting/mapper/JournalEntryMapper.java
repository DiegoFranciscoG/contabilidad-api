package io.github.diegofranciscog.accounting.mapper;

import io.github.diegofranciscog.accounting.domain.JournalEntry;
import io.github.diegofranciscog.accounting.domain.JournalLine;
import io.github.diegofranciscog.accounting.dto.JournalEntryResponse;
import io.github.diegofranciscog.accounting.dto.JournalEntrySummary;
import io.github.diegofranciscog.accounting.dto.JournalLineResponse;
import org.springframework.stereotype.Component;

@Component
public class JournalEntryMapper {

    /** Human-readable entry number: fiscal year and sequence, for example 2026-15. */
    public static String number(int fiscalYear, Integer sequence) {
        return sequence == null ? null : fiscalYear + "-" + sequence;
    }

    public JournalEntryResponse toResponse(JournalEntry entry, Long reversedByEntryId) {
        return new JournalEntryResponse(
                entry.getId(),
                number(entry.getFiscalYear(), entry.getEntryNumber()),
                entry.getEntryDate(),
                entry.getPeriod().label(),
                entry.getDescription(),
                entry.getStatus(),
                entry.getEntryType(),
                entry.getSource(),
                entry.getSourceReference(),
                entry.getReversesEntry() == null ? null : entry.getReversesEntry().getId(),
                reversedByEntryId,
                entry.getTotalDebit(),
                entry.getTotalCredit(),
                entry.getCreatedBy(),
                entry.getCreatedAt(),
                entry.getPostedAt(),
                entry.getLines().stream().map(this::toResponse).toList());
    }

    public JournalEntrySummary toSummary(JournalEntry entry) {
        return new JournalEntrySummary(entry.getId(), number(entry.getFiscalYear(), entry.getEntryNumber()),
                entry.getEntryDate(), entry.getDescription(), entry.getStatus(), entry.getEntryType(),
                entry.getSource(), entry.getTotalDebit());
    }

    private JournalLineResponse toResponse(JournalLine line) {
        return new JournalLineResponse(line.getLineNumber(), line.getAccount().getCode(), line.getAccount().getName(),
                line.getCostCenter() == null ? null : line.getCostCenter().getCode(),
                line.debit(), line.credit(), line.getDescription());
    }
}
