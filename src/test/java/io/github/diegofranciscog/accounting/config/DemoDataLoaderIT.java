package io.github.diegofranciscog.accounting.config;

import io.github.diegofranciscog.accounting.AbstractIntegrationTest;
import io.github.diegofranciscog.accounting.domain.PeriodStatus;
import io.github.diegofranciscog.accounting.dto.FiscalPeriodResponse;
import io.github.diegofranciscog.accounting.dto.LoginRequest;
import io.github.diegofranciscog.accounting.dto.TokenResponse;
import io.github.diegofranciscog.accounting.dto.report.BalanceSheetReport;
import io.github.diegofranciscog.accounting.dto.report.IncomeStatementReport;
import io.github.diegofranciscog.accounting.service.FiscalPeriodService;
import io.github.diegofranciscog.accounting.service.ReportService;
import io.github.diegofranciscog.accounting.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs in its own context (and database) because it loads the current year. */
class DemoDataLoaderIT extends AbstractIntegrationTest {

    private static final String DEMO_PASSWORD = "demo-" + UUID.randomUUID();

    @DynamicPropertySource
    static void demo(DynamicPropertyRegistry registry) {
        registry.add("app.bootstrap.demo-data", () -> "true");
        registry.add("app.bootstrap.demo-password", () -> DEMO_PASSWORD);
    }

    @Autowired
    private DemoDataLoader loader;

    @Autowired
    private ReportService reports;

    @Autowired
    private FiscalPeriodService periods;

    @Autowired
    private UserService users;

    @Autowired
    private Clock clock;

    @Test
    void demoCompanyIsConsistentAndLoadsOnce() {
        LocalDate today = LocalDate.now(clock);
        int year = today.getYear();

        BalanceSheetReport sheet = reports.balanceSheet(year, today.getMonthValue(), 3);
        IncomeStatementReport statement = reports.incomeStatement(year, 1, today.getMonthValue(), null, 3);

        assertThat(sheet.balanced()).isTrue();
        assertThat(sheet.totalAssets()).isPositive();
        assertThat(reports.trialBalance(year, 1, today.getMonthValue()).balanced()).isTrue();
        assertThat(statement.revenue()).isPositive();
        assertThat(sheet.currentResult()).isEqualByComparingTo(statement.netProfit());

        List<FiscalPeriodResponse> yearPeriods = periods.list(year);
        assertThat(yearPeriods).filteredOn(period -> period.month() < today.getMonthValue())
                .allMatch(period -> period.status() == PeriodStatus.CLOSED);
        assertThat(yearPeriods).filteredOn(period -> period.month() >= today.getMonthValue())
                .allMatch(period -> period.status() == PeriodStatus.OPEN);

        int entriesBefore = reports.journal(LocalDate.of(year, 1, 1), today).entries().size();
        loader.run(new DefaultApplicationArguments());
        assertThat(entriesBefore).isGreaterThan(20);
        assertThat(reports.journal(LocalDate.of(year, 1, 1), today).entries()).hasSize(entriesBefore);

        TokenResponse token = users.login(new LoginRequest(DemoDataLoader.DEMO_VIEWER, DEMO_PASSWORD), "10.1.1.1");
        assertThat(token.role().name()).isEqualTo("VIEWER");
    }
}
