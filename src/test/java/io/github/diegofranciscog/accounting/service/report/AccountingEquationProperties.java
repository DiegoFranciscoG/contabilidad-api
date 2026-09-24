package io.github.diegofranciscog.accounting.service.report;

import io.github.diegofranciscog.accounting.domain.Account;
import io.github.diegofranciscog.accounting.domain.AccountElement;
import io.github.diegofranciscog.accounting.domain.AccountMovement;
import io.github.diegofranciscog.accounting.domain.EntryRules;
import io.github.diegofranciscog.accounting.domain.LineSpec;
import io.github.diegofranciscog.accounting.domain.Money;
import io.github.diegofranciscog.accounting.domain.TestChart;
import io.github.diegofranciscog.accounting.dto.report.BalanceSheetReport;
import io.github.diegofranciscog.accounting.dto.report.IncomeStatementReport;
import io.github.diegofranciscog.accounting.dto.report.TrialBalanceReport;
import io.github.diegofranciscog.accounting.exception.DomainException;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Acceptance criterion: any set of balanced entries keeps the accounting equation
 * Assets = Liabilities + Equity + (Income − Expenses) (R7), whatever accounts and amounts they use,
 * including contra accounts with the opposite nature.
 */
class AccountingEquationProperties {

    private static final List<Account> ACCOUNTS = List.of(TestChart.ASSETS, TestChart.CASH, TestChart.RECEIVABLES,
            TestChart.DEPRECIATION, TestChart.LIABILITIES, TestChart.PAYABLES, TestChart.EQUITY, TestChart.CAPITAL,
            TestChart.PROFIT, TestChart.LOSS, TestChart.INCOME, TestChart.SALES, TestChart.EXPENSES, TestChart.SALARIES);
    private static final List<Account> DETAIL = ACCOUNTS.stream().filter(Account::isAcceptsEntries).toList();
    private static final List<AccountNode> NODES = ACCOUNTS.stream().map(AccountNode::of).toList();
    private static final LocalDate AS_OF = LocalDate.of(2026, 12, 31);

    @Property(tries = 300)
    void balancedEntriesKeepTheAccountingEquation(@ForAll("ledgers") List<List<LineSpec>> entries) {
        Map<Long, AccountMovement> movements = movements(entries);

        BalanceSheetReport sheet = BalanceSheetBuilder.build(AS_OF, NODES, movements, 3);

        assertThat(sheet.balanced()).isTrue();
        assertThat(sheet.totalAssets()).isEqualByComparingTo(sheet.totalLiabilitiesAndEquity());
        assertThat(sheet.totalAssets())
                .isEqualByComparingTo(sheet.totalLiabilities().add(sheet.totalEquity()));
    }

    @Property(tries = 300)
    void trialBalanceAlwaysSquares(@ForAll("ledgers") List<List<LineSpec>> opening,
                                   @ForAll("ledgers") List<List<LineSpec>> period) {
        TrialBalanceReport trial = TrialBalanceBuilder.build(2026, 1, 12, NODES, movements(opening), movements(period));

        assertThat(trial.balanced()).isTrue();
        assertThat(trial.totalDebit()).isEqualByComparingTo(trial.totalCredit());
        assertThat(trial.totalDebitBalance()).isEqualByComparingTo(trial.totalCreditBalance());
    }

    @Property(tries = 300)
    void netProfitIsIncomeMinusExpensesAndFeedsEquity(@ForAll("ledgers") List<List<LineSpec>> entries) {
        Map<Long, AccountMovement> movements = movements(entries);
        BigDecimal income = Money.ZERO;
        BigDecimal expenses = Money.ZERO;
        for (Account account : DETAIL) {
            AccountMovement movement = movements.getOrDefault(account.getId(), AccountMovement.empty(account.getId()));
            if (account.getElement() == AccountElement.INCOME) {
                income = income.add(movement.credit().subtract(movement.debit()));
            } else if (account.getElement() == AccountElement.EXPENSE) {
                expenses = expenses.add(movement.debit().subtract(movement.credit()));
            }
        }

        IncomeStatementReport statement = IncomeStatementBuilder.build(2026, 1, 12, null, NODES, movements, 3);
        BalanceSheetReport sheet = BalanceSheetBuilder.build(AS_OF, NODES, movements, 3);

        assertThat(statement.netProfit()).isEqualByComparingTo(income.subtract(expenses));
        assertThat(sheet.currentResult()).isEqualByComparingTo(statement.netProfit());
    }

    @Property(tries = 300)
    void reversingEveryEntryLeavesAllBalancesAtZero(@ForAll("ledgers") List<List<LineSpec>> entries) {
        List<List<LineSpec>> withReversals = new ArrayList<>(entries);
        entries.forEach(entry -> withReversals.add(entry.stream().map(LineSpec::mirrored).toList()));

        movements(withReversals).values()
                .forEach(movement -> assertThat(movement.net()).isEqualByComparingTo(BigDecimal.ZERO));
    }

    @Property(tries = 300)
    void anyUnbalancedEntryIsRejected(@ForAll("entries") List<LineSpec> entry,
                                      @ForAll @IntRange(min = 1, max = 1_000_000) int cents) {
        List<LineSpec> tampered = new ArrayList<>(entry);
        LineSpec first = tampered.getFirst();
        BigDecimal delta = BigDecimal.valueOf(cents, 2);
        tampered.set(0, first.debit().signum() > 0
                ? new LineSpec(first.account(), null, first.debit().add(delta), first.credit(), null)
                : new LineSpec(first.account(), null, first.debit(), first.credit().add(delta), null));

        assertThatThrownBy(() -> EntryRules.requireBalanced(tampered))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("no cuadra");
    }

    @Provide
    Arbitrary<List<List<LineSpec>>> ledgers() {
        return entries().list().ofMinSize(0).ofMaxSize(25);
    }

    /** A balanced entry: debit amounts on random accounts, and the same total split across credit lines. */
    @Provide
    Arbitrary<List<LineSpec>> entries() {
        Arbitrary<Account> account = Arbitraries.of(DETAIL);
        Arbitrary<List<Integer>> debitCents = Arbitraries.integers().between(1, 5_000_000).list().ofMinSize(1).ofMaxSize(4);
        Arbitrary<List<Account>> debitAccounts = account.list().ofSize(4);
        Arbitrary<List<Account>> creditAccounts = account.list().ofSize(4);
        Arbitrary<Integer> creditParts = Arbitraries.integers().between(1, 4);
        Arbitrary<Long> seed = Arbitraries.longs();

        return Combinators.combine(debitCents, debitAccounts, creditAccounts, creditParts, seed)
                .as((debits, debitAccountList, creditAccountList, parts, randomSeed) -> {
                    List<LineSpec> lines = new ArrayList<>();
                    long total = 0;
                    for (int i = 0; i < debits.size(); i++) {
                        total += debits.get(i);
                        lines.add(LineSpec.debit(debitAccountList.get(i), BigDecimal.valueOf(debits.get(i), 2), null, null));
                    }
                    long[] credits = split(total, (int) Math.min(parts, total), randomSeed);
                    for (int i = 0; i < credits.length; i++) {
                        lines.add(LineSpec.credit(creditAccountList.get(i), BigDecimal.valueOf(credits[i], 2), null, null));
                    }
                    EntryRules.requireBalanced(lines);
                    return lines;
                });
    }

    /** Splits {@code total} cents into {@code parts} positive amounts. */
    private static long[] split(long total, int parts, long seed) {
        long[] result = new long[parts];
        long remaining = total;
        Random random = new Random(seed);
        for (int i = 0; i < parts - 1; i++) {
            long maxForThis = remaining - (parts - 1 - i);
            result[i] = 1 + (long) (random.nextDouble() * (maxForThis - 1));
            remaining -= result[i];
        }
        result[parts - 1] = remaining;
        return result;
    }

    private static Map<Long, AccountMovement> movements(List<List<LineSpec>> entries) {
        Map<Long, AccountMovement> movements = new HashMap<>();
        entries.stream().flatMap(List::stream).forEach(line -> movements.merge(line.account().getId(),
                new AccountMovement(line.account().getId(), Money.of(line.debit()), Money.of(line.credit())),
                AccountMovement::plus));
        return movements;
    }
}
