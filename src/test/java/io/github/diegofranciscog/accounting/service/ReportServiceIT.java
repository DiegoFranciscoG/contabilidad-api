package io.github.diegofranciscog.accounting.service;

import io.github.diegofranciscog.accounting.AbstractIntegrationTest;
import io.github.diegofranciscog.accounting.domain.EntryType;
import io.github.diegofranciscog.accounting.dto.CreateCostCenterRequest;
import io.github.diegofranciscog.accounting.dto.JournalEntryRequest;
import io.github.diegofranciscog.accounting.dto.JournalLineRequest;
import io.github.diegofranciscog.accounting.dto.ReverseEntryRequest;
import io.github.diegofranciscog.accounting.dto.report.BalanceSheetReport;
import io.github.diegofranciscog.accounting.dto.report.IncomeStatementReport;
import io.github.diegofranciscog.accounting.dto.report.JournalReport;
import io.github.diegofranciscog.accounting.dto.report.LedgerReport;
import io.github.diegofranciscog.accounting.dto.report.TrialBalanceReport;
import io.github.diegofranciscog.accounting.exception.ConflictException;
import io.github.diegofranciscog.accounting.exception.DomainException;
import io.github.diegofranciscog.accounting.mapper.ReportTabularMapper;
import io.github.diegofranciscog.accounting.service.export.ExcelExporter;
import io.github.diegofranciscog.accounting.service.export.PdfExporter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A small but complete fiscal year: opening balances, a credit sale, a cash purchase, rent paid per cost center,
 * depreciation, a reversed mistake and the year-end closing. Every report must agree with the numbers by hand.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ReportServiceIT extends AbstractIntegrationTest {

    /** Earliest year of the shared test database: cumulative balances must not include other test classes. */
    private static final int YEAR = 2030;
    private static final String USER = "contador@demo.ec";

    @Autowired
    private JournalEntryService entries;

    @Autowired
    private FiscalPeriodService periods;

    @Autowired
    private CostCenterService costCenters;

    @Autowired
    private ReportService reports;

    @Autowired
    private YearEndClosingService closing;

    @Autowired
    private ReportTabularMapper tabular;

    @Autowired
    private ExcelExporter excel;

    @Autowired
    private PdfExporter pdf;

    @BeforeAll
    void recordYear() {
        periods.openYear(YEAR);
        costCenters.create(new CreateCostCenterRequest("ADM-2030", "Administración 2030"));

        post(LocalDate.of(YEAR, 1, 2), "Asiento de apertura", EntryType.OPENING,
                line("1010103", "10000.00", null, null),
                line("1020108", "3000.00", null, null),
                line("30101", null, "13000.00", null));
        // Credit sale: 1,000 + 15 % VAT.
        post(LocalDate.of(YEAR, 1, 15), "Venta a crédito", EntryType.STANDARD,
                line("101020502", "1150.00", null, null),
                line("4101", null, "1000.00", null),
                line("201070101", null, "150.00", null));
        // Cost of the goods sold.
        post(LocalDate.of(YEAR, 1, 15), "Costo de venta", EntryType.STANDARD,
                line("5101", "600.00", null, null),
                line("1010103", null, "600.00", null));
        post(LocalDate.of(YEAR, 2, 1), "Arriendo de oficina", EntryType.STANDARD,
                line("520209", "400.00", null, "ADM-2030"),
                line("1010103", null, "400.00", null));
        post(LocalDate.of(YEAR, 2, 28), "Depreciación equipo de computación", EntryType.STANDARD,
                line("52022101", "100.00", null, null),
                line("1020112", null, "100.00", null));
        // A mistaken entry, cancelled in the next month.
        Long mistake = post(LocalDate.of(YEAR, 2, 20), "Pago duplicado", EntryType.STANDARD,
                line("520218", "80.00", null, null),
                line("1010103", null, "80.00", null));
        entries.reverse(mistake, new ReverseEntryRequest(
                LocalDate.of(YEAR, 3, 1), "Duplicado"), USER);
    }

    @Test
    @Order(1)
    void trialBalanceSquaresAndRollsUpGroups() {
        TrialBalanceReport trial = reports.trialBalance(YEAR, 1, 3);

        assertThat(trial.balanced()).isTrue();
        assertThat(trial.totalDebit()).isEqualByComparingTo("15410.00");
        assertThat(row(trial, "1010103").debitBalance()).isEqualByComparingTo("9000.00");
        assertThat(row(trial, "520218").debitBalance()).isEqualByComparingTo("0.00");
        assertThat(row(trial, "101").debitBalance()).isEqualByComparingTo("10150.00");
        assertThat(row(trial, "30101").creditBalance()).isEqualByComparingTo("13000.00");
    }

    @Test
    @Order(2)
    void trialBalanceOfLaterMonthsCarriesOpeningBalances() {
        TrialBalanceReport march = reports.trialBalance(YEAR, 3, 3);

        assertThat(march.balanced()).isTrue();
        assertThat(row(march, "1010103").openingDebit()).isEqualByComparingTo("8920.00");
        assertThat(row(march, "1010103").debit()).isEqualByComparingTo("80.00");
    }

    @Test
    @Order(3)
    void incomeStatementFollowsSuperciasSubtotals() {
        IncomeStatementReport statement = reports.incomeStatement(YEAR, 1, 12, null, 3);

        assertThat(statement.revenue()).isEqualByComparingTo("1000.00");
        assertThat(statement.grossProfit()).isEqualByComparingTo("400.00");
        assertThat(statement.profitBeforeParticipationAndTax()).isEqualByComparingTo("-100.00");
        assertThat(statement.netProfit()).isEqualByComparingTo("-100.00");
    }

    @Test
    @Order(4)
    void incomeStatementByCostCenter() {
        IncomeStatementReport administration = reports.incomeStatement(YEAR, 1, 12, "ADM-2030", 3);

        assertThat(administration.revenue()).isEqualByComparingTo("0.00");
        assertThat(administration.netProfit()).isEqualByComparingTo("-400.00");
    }

    @Test
    @Order(5)
    void balanceSheetKeepsTheEquationWithContraAccounts() {
        BalanceSheetReport sheet = reports.balanceSheet(YEAR, 3, 3);

        assertThat(sheet.balanced()).isTrue();
        // Bank 9,000 + receivable 1,150 + equipment 3,000 − depreciation 100.
        assertThat(sheet.totalAssets()).isEqualByComparingTo("13050.00");
        assertThat(sheet.totalLiabilities()).isEqualByComparingTo("150.00");
        assertThat(sheet.currentResult()).isEqualByComparingTo("-100.00");
        assertThat(sheet.totalEquity()).isEqualByComparingTo("12900.00");
    }

    @Test
    @Order(6)
    void ledgerShowsRunningBalance() {
        LedgerReport bank = reports.ledger("1010103", LocalDate.of(YEAR, 1, 1), LocalDate.of(YEAR, 3, 31));

        assertThat(bank.openingBalance()).isEqualByComparingTo("0.00");
        assertThat(bank.lines()).extracting(LedgerReport.Line::balance).extracting(BigDecimal::toPlainString)
                .containsExactly("10000.00", "9400.00", "9000.00", "8920.00", "9000.00");
        assertThat(bank.closingBalance()).isEqualByComparingTo("9000.00");

        LedgerReport februaryOnwards = reports.ledger("10101", LocalDate.of(YEAR, 2, 1), LocalDate.of(YEAR, 3, 31));
        assertThat(februaryOnwards.openingBalance()).isEqualByComparingTo("9400.00");
    }

    @Test
    @Order(7)
    void journalListsPostedEntriesInOrder() {
        JournalReport journal = reports.journal(LocalDate.of(YEAR, 1, 1), LocalDate.of(YEAR, 3, 31));

        assertThat(journal.entries()).hasSize(7);
        assertThat(journal.entries().getFirst().type()).isEqualTo(EntryType.OPENING);
        assertThat(journal.totalDebit()).isEqualByComparingTo(journal.totalCredit());
        assertThat(journal.totalDebit()).isEqualByComparingTo("15410.00");
    }

    @Test
    @Order(8)
    void reportsExportToExcelAndPdf() {
        byte[] xlsx = excel.export(tabular.trialBalance(reports.trialBalance(YEAR, 1, 3)));
        byte[] statementPdf = pdf.export(tabular.incomeStatement(reports.incomeStatement(YEAR, 1, 12, null, 3)));
        byte[] journalPdf = pdf.export(tabular.journal(reports.journal(LocalDate.of(YEAR, 1, 1), LocalDate.of(YEAR, 1, 31))));
        byte[] ledgerXlsx = excel.export(tabular.ledger(reports.ledger("1010103", LocalDate.of(YEAR, 1, 1), LocalDate.of(YEAR, 1, 31))));
        byte[] sheetXlsx = excel.export(tabular.balanceSheet(reports.balanceSheet(YEAR, 3, 4)));

        assertThat(new String(xlsx, 0, 2, StandardCharsets.US_ASCII)).isEqualTo("PK");
        assertThat(new String(ledgerXlsx, 0, 2, StandardCharsets.US_ASCII)).isEqualTo("PK");
        assertThat(new String(sheetXlsx, 0, 2, StandardCharsets.US_ASCII)).isEqualTo("PK");
        assertThat(new String(statementPdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        assertThat(new String(journalPdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }

    @Test
    @Order(9)
    void invalidRangesAreRejected() {
        assertThatThrownBy(() -> reports.journal(LocalDate.of(YEAR, 3, 1), LocalDate.of(YEAR, 1, 1)))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> reports.journal(LocalDate.of(YEAR, 1, 1), LocalDate.of(YEAR + 2, 1, 1)))
                .isInstanceOf(DomainException.class).hasMessageContaining("rango máximo");
        assertThatThrownBy(() -> reports.trialBalance(YEAR, 5, 2)).isInstanceOf(DomainException.class);
    }

    @Test
    @Order(10)
    void yearEndClosingMovesResultToEquityOnlyOnce() {
        var closingEntry = closing.close(YEAR, USER);

        assertThat(closingEntry.type()).isEqualTo(EntryType.CLOSING);
        assertThat(closingEntry.lines()).anySatisfy(line -> {
            assertThat(line.accountCode()).isEqualTo("30702");
            assertThat(line.debit()).isEqualByComparingTo("100.00");
        });

        BalanceSheetReport afterClosing = reports.balanceSheet(YEAR, 12, 3);
        assertThat(afterClosing.balanced()).isTrue();
        assertThat(afterClosing.currentResult()).isEqualByComparingTo("0.00");
        assertThat(afterClosing.totalEquity()).isEqualByComparingTo("12900.00");

        // The income statement ignores the closing entry.
        assertThat(reports.incomeStatement(YEAR, 1, 12, null, 3).netProfit()).isEqualByComparingTo("-100.00");
        assertThatThrownBy(() -> closing.close(YEAR, USER)).isInstanceOf(ConflictException.class);
    }

    private Long post(LocalDate date, String description, EntryType type, JournalLineRequest... lines) {
        return entries.create(new JournalEntryRequest(date, description, type, List.of(lines), true), USER).id();
    }

    private static JournalLineRequest line(String account, String debit, String credit, String costCenter) {
        return new JournalLineRequest(account, costCenter, debit == null ? null : new BigDecimal(debit),
                credit == null ? null : new BigDecimal(credit), null);
    }

    private static TrialBalanceReport.Row row(TrialBalanceReport trial, String code) {
        return trial.rows().stream().filter(row -> row.code().equals(code)).findFirst().orElseThrow();
    }
}
