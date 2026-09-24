package io.github.diegofranciscog.accounting.dto.report;

import io.github.diegofranciscog.accounting.domain.Nature;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Libro mayor of one account (a group account includes all its sub-accounts). Balances follow the nature. */
public record LedgerReport(String accountCode, String accountName, Nature nature, LocalDate from, LocalDate to,
                           BigDecimal openingBalance, List<Line> lines, BigDecimal totalDebit, BigDecimal totalCredit,
                           BigDecimal closingBalance) {

    public record Line(LocalDate date, String entryNumber, Long entryId, String accountCode, String description,
                       String costCenterCode, BigDecimal debit, BigDecimal credit, BigDecimal balance) {
    }
}
