package io.github.diegofranciscog.accounting.domain;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.concurrent.atomic.AtomicLong;

/** In-memory accounts and periods for unit tests (no database). */
public final class TestChart {

    private static final AtomicLong IDS = new AtomicLong(1);

    public static final Account ASSETS = withId(Account.create("1", "ACTIVO", null, null, false));
    public static final Account CASH = withId(Account.create("1010101", "CAJA", ASSETS, null, true));
    public static final Account RECEIVABLES = withId(Account.create("1010205", "CLIENTES", ASSETS, null, true));
    public static final Account DEPRECIATION = withId(Account.create("1020112", "(-) DEPRECIACIÓN", ASSETS, Nature.CREDIT, true));
    public static final Account LIABILITIES = withId(Account.create("2", "PASIVO", null, null, false));
    public static final Account PAYABLES = withId(Account.create("2010301", "PROVEEDORES", LIABILITIES, null, true));
    public static final Account EQUITY = withId(Account.create("3", "PATRIMONIO", null, null, false));
    public static final Account CAPITAL = withId(Account.create("30101", "CAPITAL", EQUITY, null, true));
    public static final Account PROFIT = withId(Account.create("30701", "GANANCIA", EQUITY, null, true));
    public static final Account LOSS = withId(Account.create("30702", "(-) PÉRDIDA", EQUITY, Nature.DEBIT, true));
    public static final Account INCOME = withId(Account.create("4", "INGRESOS", null, null, false));
    public static final Account SALES = withId(Account.create("4101", "VENTAS", INCOME, null, true));
    public static final Account EXPENSES = withId(Account.create("5", "GASTOS", null, null, false));
    public static final Account SALARIES = withId(Account.create("520201", "SUELDOS", EXPENSES, null, true));

    private TestChart() {
    }

    public static FiscalPeriod openPeriod(int year, int month) {
        return new FiscalPeriod(YearMonth.of(year, month));
    }

    public static LineSpec debit(Account account, String amount) {
        return LineSpec.debit(account, new BigDecimal(amount), null, null);
    }

    public static LineSpec credit(Account account, String amount) {
        return LineSpec.credit(account, new BigDecimal(amount), null, null);
    }

    /** Gives in-memory entities an id so report builders can key them. */
    public static <T> T withId(T entity) {
        try {
            Field id = entity.getClass().getDeclaredField("id");
            id.setAccessible(true);
            id.set(entity, IDS.getAndIncrement());
            return entity;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
