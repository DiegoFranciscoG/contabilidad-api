package io.github.diegofranciscog.accounting.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BalanceViewRepository {

    private final JdbcTemplate jdbc;

    public BalanceViewRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** CONCURRENTLY keeps the view readable by reports while it is being rebuilt. */
    public void refresh() {
        jdbc.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY mv_account_period_balances");
    }
}
