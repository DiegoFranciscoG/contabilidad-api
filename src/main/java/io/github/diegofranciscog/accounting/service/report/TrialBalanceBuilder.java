package io.github.diegofranciscog.accounting.service.report;

import io.github.diegofranciscog.accounting.domain.AccountMovement;
import io.github.diegofranciscog.accounting.domain.Money;
import io.github.diegofranciscog.accounting.dto.report.TrialBalanceReport;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Balance de comprobación de sumas y saldos: opening balance, period sums and closing balance per account. */
public final class TrialBalanceBuilder {

    private TrialBalanceBuilder() {
    }

    public static TrialBalanceReport build(int year, int fromMonth, int toMonth, List<AccountNode> accounts,
                                           Map<Long, AccountMovement> opening, Map<Long, AccountMovement> period) {
        AccountTree openingTree = new AccountTree(accounts, opening);
        AccountTree periodTree = new AccountTree(accounts, period);

        List<TrialBalanceReport.Row> rows = new ArrayList<>();
        BigDecimal totalOpeningDebit = Money.ZERO;
        BigDecimal totalOpeningCredit = Money.ZERO;
        BigDecimal totalDebit = Money.ZERO;
        BigDecimal totalCredit = Money.ZERO;
        BigDecimal totalDebitBalance = Money.ZERO;
        BigDecimal totalCreditBalance = Money.ZERO;

        for (AccountNode node : periodTree.nodes()) {
            AccountMovement before = openingTree.movement(node);
            AccountMovement during = periodTree.movement(node);
            if (before.isZero() && during.isZero()) {
                continue;
            }
            BigDecimal openingNet = before.net();
            BigDecimal closingNet = openingNet.add(during.net());
            TrialBalanceReport.Row row = new TrialBalanceReport.Row(node.code(), node.name(), node.level(), node.detail(),
                    positive(openingNet), positive(openingNet.negate()), during.debit(), during.credit(),
                    positive(closingNet), positive(closingNet.negate()));
            rows.add(row);
            if (node.detail()) {
                totalOpeningDebit = totalOpeningDebit.add(row.openingDebit());
                totalOpeningCredit = totalOpeningCredit.add(row.openingCredit());
                totalDebit = totalDebit.add(row.debit());
                totalCredit = totalCredit.add(row.credit());
                totalDebitBalance = totalDebitBalance.add(row.debitBalance());
                totalCreditBalance = totalCreditBalance.add(row.creditBalance());
            }
        }
        boolean balanced = totalOpeningDebit.compareTo(totalOpeningCredit) == 0
                && totalDebit.compareTo(totalCredit) == 0
                && totalDebitBalance.compareTo(totalCreditBalance) == 0;
        return new TrialBalanceReport(year, fromMonth, toMonth, rows, totalOpeningDebit, totalOpeningCredit,
                totalDebit, totalCredit, totalDebitBalance, totalCreditBalance, balanced);
    }

    private static BigDecimal positive(BigDecimal value) {
        return value.signum() > 0 ? value : Money.ZERO;
    }
}
