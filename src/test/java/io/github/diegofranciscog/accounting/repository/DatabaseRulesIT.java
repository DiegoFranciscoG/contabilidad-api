package io.github.diegofranciscog.accounting.repository;

import io.github.diegofranciscog.accounting.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The database repeats the accounting rules as a safety net: these tests bypass the domain and write raw SQL.
 * Each test commits for real (deferred triggers only run at COMMIT) and uses its own fiscal year.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DatabaseRulesIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate tx;

    @Test
    void chartOfAccountsIsSeededFromSupercias() {
        Integer accounts = jdbc.queryForObject("SELECT count(*) FROM accounts", Integer.class);
        Boolean cashAcceptsEntries = jdbc.queryForObject("SELECT accepts_entries FROM accounts WHERE code = '1010101'", Boolean.class);
        String depreciationNature = jdbc.queryForObject("SELECT nature FROM accounts WHERE code = '1020112'", String.class);
        Integer mappings = jdbc.queryForObject("SELECT count(*) FROM event_account_mappings", Integer.class);

        assertThat(accounts).isGreaterThan(100);
        assertThat(cashAcceptsEntries).isTrue();
        assertThat(depreciationNature).isEqualTo("CREDIT");
        assertThat(mappings).isEqualTo(10);
    }

    @Test
    void balancedEntryCommits() {
        long period = openPeriod(2031, 1);

        long entry = tx.execute(status -> {
            long id = insertEntry(period, "2031-01-15", "100.00");
            insertLine(id, 1, "1010101", "100.00", "0");
            insertLine(id, 2, "4101", "0", "100.00");
            return id;
        });

        assertThat(jdbc.queryForObject("SELECT count(*) FROM journal_lines WHERE entry_id = ?", Integer.class, entry)).isEqualTo(2);
    }

    @Test
    void unbalancedEntryIsRejectedAtCommit() {
        long period = openPeriod(2032, 1);

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            long id = insertEntry(period, "2032-01-15", "100.00");
            insertLine(id, 1, "1010101", "100.00", "0");
            insertLine(id, 2, "4101", "0", "90.00");
        })).rootCause().hasMessageContaining("accounting_rule").hasMessageContaining("unbalanced");
    }

    @Test
    void entryWithOneLineIsRejected() {
        long period = openPeriod(2033, 1);

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            long id = insertEntry(period, "2033-01-15", "100.00");
            insertLine(id, 1, "1010101", "100.00", "0");
        })).rootCause().hasMessageContaining("at least two lines");
    }

    @Test
    void headerTotalsMustMatchLines() {
        long period = openPeriod(2034, 1);

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            long id = insertEntry(period, "2034-01-15", "50.00");
            insertLine(id, 1, "1010101", "100.00", "0");
            insertLine(id, 2, "4101", "0", "100.00");
        })).rootCause().hasMessageContaining("totals do not match");
    }

    @Test
    void postedEntryIsImmutable() {
        long period = openPeriod(2035, 1);
        long entry = tx.execute(status -> {
            long id = insertEntry(period, "2035-01-15", "100.00");
            insertLine(id, 1, "1010101", "100.00", "0");
            insertLine(id, 2, "4101", "0", "100.00");
            return id;
        });
        jdbc.update("UPDATE journal_entries SET status = 'POSTED', entry_number = 1, posted_at = now() WHERE id = ?", entry);

        assertThatThrownBy(() -> jdbc.update("UPDATE journal_entries SET description = 'cambio' WHERE id = ?", entry))
                .rootCause().hasMessageContaining("immutable");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM journal_entries WHERE id = ?", entry))
                .rootCause().hasMessageContaining("immutable");
        assertThatThrownBy(() -> jdbc.update("UPDATE journal_lines SET debit = 1 WHERE entry_id = ? AND line_number = 1", entry))
                .rootCause().hasMessageContaining("immutable");
    }

    @Test
    void closedPeriodRejectsEntriesAndNeverReopens() {
        long period = openPeriod(2036, 1);
        jdbc.update("UPDATE fiscal_periods SET status = 'CLOSED', closed_at = now() WHERE id = ?", period);

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> insertEntry(period, "2036-01-15", "10.00")))
                .rootCause().hasMessageContaining("is closed");
        assertThatThrownBy(() -> jdbc.update("UPDATE fiscal_periods SET status = 'OPEN', closed_at = NULL WHERE id = ?", period))
                .rootCause().hasMessageContaining("immutable");
    }

    @Test
    void groupAccountsRejectLines() {
        long period = openPeriod(2037, 1);

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            long id = insertEntry(period, "2037-01-15", "10.00");
            insertLine(id, 1, "101", "10.00", "0");
            insertLine(id, 2, "4101", "0", "10.00");
        })).rootCause().hasMessageContaining("group account");
    }

    @Test
    void lineCannotHaveDebitAndCredit() {
        long period = openPeriod(2038, 1);

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            long id = insertEntry(period, "2038-01-15", "10.00");
            insertLine(id, 1, "1010101", "10.00", "10.00");
        })).rootCause().hasMessageContaining("ck_journal_lines_one_side");
    }

    @Test
    void childCodeMustExtendParentCode() {
        Long assets = jdbc.queryForObject("SELECT id FROM accounts WHERE code = '101'", Long.class);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO accounts (code, name, parent_id, level, element, nature, accepts_entries)
                VALUES ('1029', 'MAL UBICADA', ?, 3, 'ASSET', 'DEBIT', TRUE)""", assets))
                .rootCause().hasMessageContaining("must start with parent code");
    }

    @Test
    void materializedViewAggregatesPostedEntries() {
        long period = openPeriod(2039, 1);
        long entry = tx.execute(status -> {
            long id = insertEntry(period, "2039-01-15", "80.00");
            insertLine(id, 1, "1010101", "80.00", "0");
            insertLine(id, 2, "4101", "0", "80.00");
            return id;
        });
        jdbc.update("UPDATE journal_entries SET status = 'POSTED', entry_number = 1, posted_at = now() WHERE id = ?", entry);
        jdbc.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY mv_account_period_balances");

        BigDecimal cash = jdbc.queryForObject("""
                SELECT b.debit_total FROM mv_account_period_balances b JOIN accounts a ON a.id = b.account_id
                WHERE a.code = '1010101' AND b.period_id = ?""", BigDecimal.class, period);

        assertThat(cash).isEqualByComparingTo("80.00");
    }

    private long openPeriod(int year, int month) {
        return jdbc.queryForObject("""
                INSERT INTO fiscal_periods (year, month, start_date, end_date)
                VALUES (?, ?, make_date(?, ?, 1), (make_date(?, ?, 1) + INTERVAL '1 month' - INTERVAL '1 day')::date)
                RETURNING id""", Long.class, year, month, year, month, year, month);
    }

    private long insertEntry(long period, String date, String total) {
        return jdbc.queryForObject("""
                INSERT INTO journal_entries (fiscal_year, entry_date, period_id, description, total_debit, total_credit, created_by)
                VALUES (EXTRACT(YEAR FROM ?::date), ?::date, ?, 'prueba', ?::numeric, ?::numeric, 'test')
                RETURNING id""", Long.class, date, date, period, total, total);
    }

    private void insertLine(long entry, int line, String accountCode, String debit, String credit) {
        jdbc.update("""
                INSERT INTO journal_lines (entry_id, line_number, account_id, debit, credit)
                SELECT ?, ?, id, ?::numeric, ?::numeric FROM accounts WHERE code = ?""",
                entry, line, debit, credit, accountCode);
    }
}
