package io.github.diegofranciscog.accounting.service;

import io.github.diegofranciscog.accounting.AbstractIntegrationTest;
import io.github.diegofranciscog.accounting.dto.JournalEntryRequest;
import io.github.diegofranciscog.accounting.dto.JournalLineRequest;
import io.github.diegofranciscog.accounting.dto.report.BalanceSheetReport;
import io.github.diegofranciscog.accounting.dto.report.TrialBalanceReport;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end version of the property: random balanced entries go through the service, the triggers and the
 * materialized view, and the statements built from PostgreSQL still satisfy the accounting equation.
 */
class AccountingEquationIT extends AbstractIntegrationTest {

    private static final int YEAR = 2045;
    private static final List<String> DETAIL_ACCOUNTS = List.of("1010101", "1010103", "101020502", "1020108",
            "1020112", "2010301", "201070101", "30101", "30602", "4101", "4102", "4110", "5101", "520201", "52022101",
            "5302");

    @Autowired
    private JournalEntryService entries;

    @Autowired
    private FiscalPeriodService periods;

    @Autowired
    private ReportService reports;

    @Test
    void randomBalancedEntriesKeepEquationInDatabase() {
        periods.openYear(YEAR);
        List<JournalEntryRequest> generated = balancedEntries().sampleStream().limit(60).toList();

        generated.forEach(request -> entries.create(request, "propiedades@demo.ec"));

        for (int month = 1; month <= 12; month++) {
            BalanceSheetReport sheet = reports.balanceSheet(YEAR, month, 3);
            assertThat(sheet.balanced()).as("equation at month %d", month).isTrue();
        }
        TrialBalanceReport trial = reports.trialBalance(YEAR, 1, 12);
        assertThat(trial.balanced()).isTrue();
        assertThat(reports.incomeStatement(YEAR, 1, 12, null, 3).netProfit())
                .isEqualByComparingTo(reports.balanceSheet(YEAR, 12, 3).currentResult());
    }

    private static Arbitrary<JournalEntryRequest> balancedEntries() {
        Arbitrary<String> account = Arbitraries.of(DETAIL_ACCOUNTS);
        Arbitrary<List<Integer>> cents = Arbitraries.integers().between(1, 2_000_000).list().ofMinSize(1).ofMaxSize(3);
        Arbitrary<List<String>> debitAccounts = account.list().ofSize(3);
        Arbitrary<String> creditAccount = account;
        Arbitrary<LocalDate> date = Arbitraries.integers().between(1, 365).map(day -> LocalDate.ofYearDay(YEAR, day));

        return Combinators.combine(cents, debitAccounts, creditAccount, date).as((amounts, debits, credit, day) -> {
            List<JournalLineRequest> lines = new ArrayList<>();
            long total = 0;
            for (int i = 0; i < amounts.size(); i++) {
                total += amounts.get(i);
                lines.add(new JournalLineRequest(debits.get(i), null, BigDecimal.valueOf(amounts.get(i), 2), null, null));
            }
            lines.add(new JournalLineRequest(credit, null, null, BigDecimal.valueOf(total, 2), null));
            return new JournalEntryRequest(day, "Asiento aleatorio", null, lines, true);
        });
    }
}
