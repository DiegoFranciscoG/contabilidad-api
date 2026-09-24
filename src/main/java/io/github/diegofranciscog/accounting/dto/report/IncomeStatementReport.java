package io.github.diegofranciscog.accounting.dto.report;

import java.math.BigDecimal;
import java.util.List;

/** Estado de resultados by function, following the Supercias subtotals (R8). */
public record IncomeStatementReport(int year, int fromMonth, int toMonth, String costCenterCode,
                                    List<StatementLine> lines, BigDecimal revenue, BigDecimal grossProfit,
                                    BigDecimal profitBeforeParticipationAndTax, BigDecimal profitBeforeTax,
                                    BigDecimal netProfit) {
}
