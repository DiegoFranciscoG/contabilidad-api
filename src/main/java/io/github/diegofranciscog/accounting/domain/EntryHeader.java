package io.github.diegofranciscog.accounting.domain;

import java.time.LocalDate;

/** Header data of a new entry; {@code sourceReference} makes automatic entries idempotent. */
public record EntryHeader(FiscalPeriod period, LocalDate date, String description, EntryType type,
                          EntrySource source, String sourceReference) {

    public static EntryHeader manual(FiscalPeriod period, LocalDate date, String description, EntryType type) {
        return new EntryHeader(period, date, description, type, EntrySource.MANUAL, null);
    }
}
