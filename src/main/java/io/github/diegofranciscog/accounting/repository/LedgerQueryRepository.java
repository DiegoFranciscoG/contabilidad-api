package io.github.diegofranciscog.accounting.repository;

import io.github.diegofranciscog.accounting.domain.AccountMovement;
import io.github.diegofranciscog.accounting.domain.EntryType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Read-only queries for reports. All SQL is parameterised. */
@Repository
public class LedgerQueryRepository {

    /** Periods are compared as year * 100 + month so a range can span years. */
    private static final String BALANCES_BY_PERIOD = """
            SELECT b.account_id, SUM(b.debit_total) AS debit, SUM(b.credit_total) AS credit
            FROM mv_account_period_balances b
                     JOIN fiscal_periods p ON p.id = b.period_id
            WHERE (p.year * 100 + p.month) BETWEEN :fromKey AND :toKey
              AND b.entry_type NOT IN (:excludedTypes)
            GROUP BY b.account_id
            """;

    private static final String MOVEMENTS_BY_COST_CENTER = """
            SELECT l.account_id, SUM(l.debit) AS debit, SUM(l.credit) AS credit
            FROM journal_lines l
                     JOIN journal_entries e ON e.id = l.entry_id
                     JOIN fiscal_periods p ON p.id = e.period_id
            WHERE e.status = 'POSTED'
              AND (p.year * 100 + p.month) BETWEEN :fromKey AND :toKey
              AND e.entry_type NOT IN (:excludedTypes)
              AND l.cost_center_id = :costCenterId
            GROUP BY l.account_id
            """;

    private static final String LIVE_BALANCES_FOR_YEAR = """
            SELECT l.account_id, SUM(l.debit) AS debit, SUM(l.credit) AS credit
            FROM journal_lines l
                     JOIN journal_entries e ON e.id = l.entry_id
            WHERE e.status = 'POSTED'
              AND e.fiscal_year = :year
              AND e.entry_type NOT IN (:excludedTypes)
            GROUP BY l.account_id
            """;

    private static final String OPENING_FOR_ACCOUNTS = """
            SELECT COALESCE(SUM(l.debit), 0) AS debit, COALESCE(SUM(l.credit), 0) AS credit
            FROM journal_lines l
                     JOIN journal_entries e ON e.id = l.entry_id
            WHERE e.status = 'POSTED'
              AND e.entry_date < :from
              AND l.account_id IN (:accountIds)
            """;

    private static final String LEDGER_LINES = """
            SELECT e.id AS entry_id, e.entry_date, e.fiscal_year, e.entry_number, e.description AS entry_description,
                   a.code AS account_code, cc.code AS cost_center_code, l.description AS line_description,
                   l.debit, l.credit
            FROM journal_lines l
                     JOIN journal_entries e ON e.id = l.entry_id
                     JOIN accounts a ON a.id = l.account_id
                     LEFT JOIN cost_centers cc ON cc.id = l.cost_center_id
            WHERE e.status = 'POSTED'
              AND e.entry_date BETWEEN :from AND :to
              AND l.account_id IN (:accountIds)
            ORDER BY e.entry_date, e.fiscal_year, e.entry_number, l.line_number
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public LedgerQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Movements per account from the materialized view for the period keys {@code fromKey..toKey} (yyyymm). */
    public Map<Long, AccountMovement> balancesByPeriod(int fromKey, int toKey, Collection<EntryType> excludedTypes) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("fromKey", fromKey)
                .addValue("toKey", toKey)
                .addValue("excludedTypes", typeNames(excludedTypes));
        return queryMovements(BALANCES_BY_PERIOD, params);
    }

    /** Same as {@link #balancesByPeriod} but filtered by cost center; reads the base tables. */
    public Map<Long, AccountMovement> movementsByCostCenter(int fromKey, int toKey, Collection<EntryType> excludedTypes,
                                                            long costCenterId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("fromKey", fromKey)
                .addValue("toKey", toKey)
                .addValue("excludedTypes", typeNames(excludedTypes))
                .addValue("costCenterId", costCenterId);
        return queryMovements(MOVEMENTS_BY_COST_CENTER, params);
    }

    /** Year movements read from the base tables (not the view) so year-end closing never uses stale data. */
    public Map<Long, AccountMovement> liveBalancesForYear(int year, Collection<EntryType> excludedTypes) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("year", year)
                .addValue("excludedTypes", typeNames(excludedTypes));
        return queryMovements(LIVE_BALANCES_FOR_YEAR, params);
    }

    public AccountMovement openingFor(Collection<Long> accountIds, LocalDate from) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("from", from)
                .addValue("accountIds", accountIds);
        return jdbc.queryForObject(OPENING_FOR_ACCOUNTS, params,
                (rs, i) -> new AccountMovement(0L, rs.getBigDecimal("debit"), rs.getBigDecimal("credit")));
    }

    public List<LedgerRow> ledgerLines(Collection<Long> accountIds, LocalDate from, LocalDate to) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("from", from)
                .addValue("to", to)
                .addValue("accountIds", accountIds);
        return jdbc.query(LEDGER_LINES, params, (rs, i) -> new LedgerRow(
                rs.getLong("entry_id"),
                rs.getObject("entry_date", LocalDate.class),
                rs.getInt("fiscal_year"),
                rs.getInt("entry_number"),
                rs.getString("entry_description"),
                rs.getString("account_code"),
                rs.getString("cost_center_code"),
                rs.getString("line_description"),
                rs.getBigDecimal("debit"),
                rs.getBigDecimal("credit")));
    }

    private Map<Long, AccountMovement> queryMovements(String sql, MapSqlParameterSource params) {
        Map<Long, AccountMovement> result = new HashMap<>();
        jdbc.query(sql, params, rs -> {
            long accountId = rs.getLong("account_id");
            result.put(accountId, new AccountMovement(accountId, rs.getBigDecimal("debit"), rs.getBigDecimal("credit")));
        });
        return result;
    }

    private static List<String> typeNames(Collection<EntryType> types) {
        // NOT IN () is invalid SQL, so an empty exclusion list uses a value no row can have.
        return types.isEmpty() ? List.of("-") : types.stream().map(Enum::name).toList();
    }

    public record LedgerRow(long entryId, LocalDate date, int fiscalYear, int entryNumber, String entryDescription,
                            String accountCode, String costCenterCode, String lineDescription,
                            BigDecimal debit, BigDecimal credit) {
    }
}
