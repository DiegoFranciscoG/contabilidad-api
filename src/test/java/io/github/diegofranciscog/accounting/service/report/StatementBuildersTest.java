package io.github.diegofranciscog.accounting.service.report;

import io.github.diegofranciscog.accounting.domain.Account;
import io.github.diegofranciscog.accounting.domain.AccountMovement;
import io.github.diegofranciscog.accounting.domain.Nature;
import io.github.diegofranciscog.accounting.domain.TestChart;
import io.github.diegofranciscog.accounting.dto.report.BalanceSheetReport;
import io.github.diegofranciscog.accounting.dto.report.IncomeStatementReport;
import io.github.diegofranciscog.accounting.dto.report.StatementLine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StatementBuildersTest {

    private static final Account EXPENSES = TestChart.withId(Account.create("5", "COSTOS Y GASTOS", null, null, false));
    private static final Account GROUP_52 = TestChart.withId(Account.create("52", "GASTOS", EXPENSES, null, false));
    private static final Account ADMIN = TestChart.withId(Account.create("5202", "GASTOS ADMINISTRATIVOS", GROUP_52, null, false));
    private static final Account RENT = TestChart.withId(Account.create("520209", "ARRENDAMIENTO", ADMIN, null, true));
    private static final Account ASSETS = TestChart.withId(Account.create("1", "ACTIVO", null, null, false));
    private static final Account NON_CURRENT = TestChart.withId(Account.create("102", "ACTIVO NO CORRIENTE", ASSETS, null, false));
    private static final Account EQUIPMENT = TestChart.withId(Account.create("1020108", "EQUIPO", NON_CURRENT, null, true));
    private static final Account DEPRECIATION = TestChart.withId(Account.create("1020112", "(-) DEPRECIACIÓN", NON_CURRENT, Nature.CREDIT, true));
    private static final Account OTHER_ASSET = TestChart.withId(Account.create("19", "ACTIVO FUERA DE GRUPO", ASSETS, null, true));
    private static final Account EQUITY = TestChart.withId(Account.create("3", "PATRIMONIO", null, null, false));
    private static final Account CAPITAL = TestChart.withId(Account.create("30101", "CAPITAL", EQUITY, null, true));

    private static final List<AccountNode> NODES = List.of(EXPENSES, GROUP_52, ADMIN, RENT, ASSETS, NON_CURRENT,
            EQUIPMENT, DEPRECIATION, OTHER_ASSET, EQUITY, CAPITAL).stream().map(AccountNode::of).toList();

    @Test
    void expenseBlocksListTheirAccountsEvenAtLowDepth() {
        IncomeStatementReport statement = IncomeStatementBuilder.build(2026, 1, 12, null, NODES,
                Map.of(RENT.getId(), movement(RENT, "400.00", "0")), 2);

        assertThat(statement.lines()).anySatisfy(line -> {
            assertThat(line.code()).isEqualTo("520209");
            assertThat(line.kind()).isEqualTo(StatementLine.Kind.DETAIL);
            assertThat(line.amount()).isEqualByComparingTo("400.00");
        });
        assertThat(statement.netProfit()).isEqualByComparingTo("-400.00");
    }

    @Test
    void contraAccountReducesItsGroupAndOtherAssetsGetTheirOwnSection() {
        BalanceSheetReport sheet = BalanceSheetBuilder.build(LocalDate.of(2026, 12, 31), NODES, Map.of(
                EQUIPMENT.getId(), movement(EQUIPMENT, "1000.00", "0"),
                DEPRECIATION.getId(), movement(DEPRECIATION, "0", "250.00"),
                OTHER_ASSET.getId(), movement(OTHER_ASSET, "50.00", "0"),
                CAPITAL.getId(), movement(CAPITAL, "0", "800.00")), 4);

        assertThat(sheet.totalAssets()).isEqualByComparingTo("800.00");
        assertThat(sheet.balanced()).isTrue();
        assertThat(sheet.lines()).anySatisfy(line -> {
            assertThat(line.code()).isEqualTo("1020112");
            assertThat(line.amount()).isEqualByComparingTo("-250.00");
        });
        assertThat(sheet.lines()).extracting(StatementLine::label).contains("OTROS ACTIVOS", "TOTAL OTROS ACTIVOS");
    }

    private static AccountMovement movement(Account account, String debit, String credit) {
        return new AccountMovement(account.getId(), new BigDecimal(debit), new BigDecimal(credit));
    }
}
