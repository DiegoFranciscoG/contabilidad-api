package io.github.diegofranciscog.accounting.service.report;

import io.github.diegofranciscog.accounting.domain.AccountElement;
import io.github.diegofranciscog.accounting.domain.AccountMovement;
import io.github.diegofranciscog.accounting.dto.report.IncomeStatementReport;
import io.github.diegofranciscog.accounting.dto.report.StatementLine;
import io.github.diegofranciscog.accounting.dto.report.StatementLine.Kind;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Estado de resultados by function (IFRS for SMEs 5.11) with the Supercias subtotals (R8):
 * revenue (41) − cost of sales (51) = gross profit; + other income − selling (5201), administrative (5202),
 * financial (5203) and other expenses = profit before 15 % workers' participation (5301) and income tax (5302).
 */
public final class IncomeStatementBuilder {

    static final String REVENUE = "41";
    static final String COST_OF_SALES = "51";
    static final String SELLING = "5201";
    static final String ADMINISTRATIVE = "5202";
    static final String FINANCIAL = "5203";
    static final String WORKERS_PARTICIPATION = "5301";
    static final String INCOME_TAX = "5302";

    private IncomeStatementBuilder() {
    }

    public static IncomeStatementReport build(int year, int fromMonth, int toMonth, String costCenterCode,
                                              List<AccountNode> accounts, Map<Long, AccountMovement> movements,
                                              int displayLevel) {
        AccountTree tree = new AccountTree(accounts, movements);
        int maxLevel = Math.max(3, displayLevel);
        List<StatementLine> lines = new ArrayList<>();

        Predicate<AccountNode> income = node -> node.element() == AccountElement.INCOME;
        Predicate<AccountNode> expense = node -> node.element() == AccountElement.EXPENSE;
        Predicate<AccountNode> otherIncome = income.and(node -> !node.isUnder(REVENUE));
        Predicate<AccountNode> otherExpense = expense.and(node -> !node.isUnder(COST_OF_SALES) && !node.isUnder(SELLING)
                && !node.isUnder(ADMINISTRATIVE) && !node.isUnder(FINANCIAL)
                && !node.isUnder(WORKERS_PARTICIPATION) && !node.isUnder(INCOME_TAX));

        BigDecimal revenue = block(lines, tree, REVENUE, 2, "INGRESOS DE ACTIVIDADES ORDINARIAS", maxLevel);
        BigDecimal cost = block(lines, tree, COST_OF_SALES, 2, "COSTO DE VENTAS Y PRODUCCIÓN", maxLevel);
        BigDecimal grossProfit = revenue.subtract(cost);
        lines.add(new StatementLine(Kind.SUBTOTAL, null, "GANANCIA BRUTA", 0, grossProfit));

        BigDecimal other = tree.sumOfDetails(otherIncome);
        if (other.signum() != 0) {
            lines.add(new StatementLine(Kind.HEADER, null, "OTROS INGRESOS", 0, null));
            lines.addAll(tree.detailAccountLines(otherIncome));
            lines.add(new StatementLine(Kind.SUBTOTAL, null, "TOTAL OTROS INGRESOS", 0, other));
        }

        lines.add(new StatementLine(Kind.HEADER, "52", "GASTOS", 0, null));
        BigDecimal selling = block(lines, tree, SELLING, 3, "GASTOS DE VENTA", maxLevel);
        BigDecimal administrative = block(lines, tree, ADMINISTRATIVE, 3, "GASTOS ADMINISTRATIVOS", maxLevel);
        BigDecimal financial = block(lines, tree, FINANCIAL, 3, "GASTOS FINANCIEROS", maxLevel);
        BigDecimal otherExpenses = tree.sumOfDetails(otherExpense);
        if (otherExpenses.signum() != 0) {
            lines.add(new StatementLine(Kind.HEADER, null, "OTROS GASTOS", 1, null));
            lines.addAll(tree.detailAccountLines(otherExpense));
            lines.add(new StatementLine(Kind.SUBTOTAL, null, "TOTAL OTROS GASTOS", 1, otherExpenses));
        }
        BigDecimal totalExpenses = selling.add(administrative).add(financial).add(otherExpenses);
        lines.add(new StatementLine(Kind.SUBTOTAL, "52", "TOTAL GASTOS", 0, totalExpenses));

        BigDecimal beforeParticipation = grossProfit.add(other).subtract(totalExpenses);
        lines.add(new StatementLine(Kind.SUBTOTAL, null,
                "GANANCIA (PÉRDIDA) ANTES DE 15% A TRABAJADORES E IMPUESTO A LA RENTA", 0, beforeParticipation));

        BigDecimal participation = tree.sumOfDetails(node -> node.isUnder(WORKERS_PARTICIPATION));
        lines.add(new StatementLine(Kind.DETAIL, WORKERS_PARTICIPATION, "15% PARTICIPACIÓN TRABAJADORES", 1, participation));
        BigDecimal beforeTax = beforeParticipation.subtract(participation);
        lines.add(new StatementLine(Kind.SUBTOTAL, null, "GANANCIA (PÉRDIDA) ANTES DE IMPUESTOS", 0, beforeTax));

        BigDecimal incomeTax = tree.sumOfDetails(node -> node.isUnder(INCOME_TAX));
        lines.add(new StatementLine(Kind.DETAIL, INCOME_TAX, "IMPUESTO A LA RENTA CAUSADO", 1, incomeTax));
        BigDecimal netProfit = beforeTax.subtract(incomeTax);
        lines.add(new StatementLine(Kind.TOTAL, null, "GANANCIA (PÉRDIDA) NETA DEL PERIODO", 0, netProfit));

        return new IncomeStatementReport(year, fromMonth, toMonth, costCenterCode, lines, revenue, grossProfit,
                beforeParticipation, beforeTax, netProfit);
    }

    private static BigDecimal block(List<StatementLine> lines, AccountTree tree, String prefix, int prefixLevel,
                                    String label, int maxLevel) {
        BigDecimal total = tree.sumOfDetails(node -> node.isUnder(prefix));
        int indent = prefixLevel - 2;
        lines.add(new StatementLine(Kind.HEADER, prefix, label, indent, null));
        // Always show at least one level below the block, whatever the requested depth.
        lines.addAll(tree.detailLines(prefix, prefixLevel, Math.max(prefixLevel + 1, maxLevel)));
        lines.add(new StatementLine(Kind.SUBTOTAL, prefix, "TOTAL " + label, indent, total));
        return total;
    }
}
