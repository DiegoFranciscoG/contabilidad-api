package io.github.diegofranciscog.accounting.dto.report;

import java.math.BigDecimal;
import java.util.List;

/**
 * Balance de comprobación de sumas y saldos. Totals add detail accounts only, so group rows are not counted twice.
 * {@code balanced} is true when debits equal credits and debit balances equal credit balances.
 */
public record TrialBalanceReport(int year, int fromMonth, int toMonth, List<Row> rows, BigDecimal totalOpeningDebit,
                                 BigDecimal totalOpeningCredit, BigDecimal totalDebit, BigDecimal totalCredit,
                                 BigDecimal totalDebitBalance, BigDecimal totalCreditBalance, boolean balanced) {

    public record Row(String code, String name, int level, boolean detail, BigDecimal openingDebit,
                      BigDecimal openingCredit, BigDecimal debit, BigDecimal credit, BigDecimal debitBalance,
                      BigDecimal creditBalance) {
    }
}
