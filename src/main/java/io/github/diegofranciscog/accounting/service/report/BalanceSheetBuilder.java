package io.github.diegofranciscog.accounting.service.report;

import io.github.diegofranciscog.accounting.domain.AccountElement;
import io.github.diegofranciscog.accounting.domain.AccountMovement;
import io.github.diegofranciscog.accounting.dto.report.BalanceSheetReport;
import io.github.diegofranciscog.accounting.dto.report.StatementLine;
import io.github.diegofranciscog.accounting.dto.report.StatementLine.Kind;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Estado de situación financiera with current / non-current classification (IFRS for SMEs 4.4, Supercias
 * codes 101/102 and 201/202). Profit or loss not yet closed is shown inside equity so the equation holds (R7).
 */
public final class BalanceSheetBuilder {

    private BalanceSheetBuilder() {
    }

    public static BalanceSheetReport build(LocalDate asOf, List<AccountNode> accounts,
                                           Map<Long, AccountMovement> cumulative, int displayLevel) {
        AccountTree tree = new AccountTree(accounts, cumulative);
        List<StatementLine> lines = new ArrayList<>();

        lines.add(header("1", "ACTIVO"));
        section(lines, tree, "101", "ACTIVO CORRIENTE", displayLevel);
        section(lines, tree, "102", "ACTIVO NO CORRIENTE", displayLevel);
        other(lines, tree, AccountElement.ASSET, "101", "102", "OTROS ACTIVOS");
        BigDecimal totalAssets = tree.sumOfDetails(node -> node.element() == AccountElement.ASSET);
        lines.add(new StatementLine(Kind.TOTAL, "1", "TOTAL ACTIVO", 0, totalAssets));

        lines.add(header("2", "PASIVO"));
        section(lines, tree, "201", "PASIVO CORRIENTE", displayLevel);
        section(lines, tree, "202", "PASIVO NO CORRIENTE", displayLevel);
        other(lines, tree, AccountElement.LIABILITY, "201", "202", "OTROS PASIVOS");
        BigDecimal totalLiabilities = tree.sumOfDetails(node -> node.element() == AccountElement.LIABILITY);
        lines.add(new StatementLine(Kind.TOTAL, "2", "TOTAL PASIVO", 0, totalLiabilities));

        BigDecimal income = tree.sumOfDetails(node -> node.element() == AccountElement.INCOME);
        BigDecimal expenses = tree.sumOfDetails(node -> node.element() == AccountElement.EXPENSE);
        BigDecimal currentResult = income.subtract(expenses);
        BigDecimal equityAccounts = tree.sumOfDetails(node -> node.element() == AccountElement.EQUITY);
        BigDecimal totalEquity = equityAccounts.add(currentResult);

        lines.add(header("3", "PATRIMONIO NETO"));
        lines.addAll(tree.detailLines("3", 1, Math.max(2, displayLevel)));
        if (currentResult.signum() != 0) {
            lines.add(new StatementLine(Kind.DETAIL, null, "RESULTADO DEL EJERCICIO NO CERRADO", 1, currentResult));
        }
        lines.add(new StatementLine(Kind.TOTAL, "3", "TOTAL PATRIMONIO NETO", 0, totalEquity));

        BigDecimal totalLiabilitiesAndEquity = totalLiabilities.add(totalEquity);
        lines.add(new StatementLine(Kind.TOTAL, null, "TOTAL PASIVO Y PATRIMONIO", 0, totalLiabilitiesAndEquity));

        return new BalanceSheetReport(asOf, lines, totalAssets, totalLiabilities, totalEquity, currentResult,
                totalLiabilitiesAndEquity, totalAssets.compareTo(totalLiabilitiesAndEquity) == 0);
    }

    private static void section(List<StatementLine> lines, AccountTree tree, String prefix, String label, int displayLevel) {
        BigDecimal total = tree.sumOfDetails(node -> node.isUnder(prefix));
        List<StatementLine> details = tree.detailLines(prefix, 2, Math.max(3, displayLevel));
        if (total.signum() == 0 && details.isEmpty()) {
            return;
        }
        lines.add(new StatementLine(Kind.HEADER, prefix, label, 1, null));
        lines.addAll(details);
        lines.add(new StatementLine(Kind.SUBTOTAL, prefix, "TOTAL " + label, 1, total));
    }

    private static void other(List<StatementLine> lines, AccountTree tree, AccountElement element, String current,
                              String nonCurrent, String label) {
        BigDecimal total = tree.sumOfDetails(node -> node.element() == element
                && !node.isUnder(current) && !node.isUnder(nonCurrent));
        if (total.signum() == 0) {
            return;
        }
        lines.add(new StatementLine(Kind.HEADER, null, label, 1, null));
        lines.addAll(tree.detailAccountLines(node -> node.element() == element
                && !node.isUnder(current) && !node.isUnder(nonCurrent)));
        lines.add(new StatementLine(Kind.SUBTOTAL, null, "TOTAL " + label, 1, total));
    }

    private static StatementLine header(String code, String label) {
        return new StatementLine(Kind.HEADER, code, label, 0, null);
    }
}
