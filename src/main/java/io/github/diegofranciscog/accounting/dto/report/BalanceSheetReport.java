package io.github.diegofranciscog.accounting.dto.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Estado de situación financiera (IFRS for SMEs section 4). {@code currentResult} is the profit or loss not yet
 * closed into equity; {@code balanced} checks Assets = Liabilities + Equity (R7).
 */
public record BalanceSheetReport(LocalDate asOf, List<StatementLine> lines, BigDecimal totalAssets,
                                 BigDecimal totalLiabilities, BigDecimal totalEquity, BigDecimal currentResult,
                                 BigDecimal totalLiabilitiesAndEquity, boolean balanced) {
}
