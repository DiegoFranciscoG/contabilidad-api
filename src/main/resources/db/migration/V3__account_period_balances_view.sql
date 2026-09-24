-- Materialized balances per account, period and entry type (only posted entries).
-- The entry type lets the income statement exclude the year-end closing entry (R10).
CREATE MATERIALIZED VIEW mv_account_period_balances AS
SELECT l.account_id,
       e.period_id,
       e.entry_type,
       SUM(l.debit)  AS debit_total,
       SUM(l.credit) AS credit_total
FROM journal_lines l
         JOIN journal_entries e ON e.id = l.entry_id
WHERE e.status = 'POSTED'
GROUP BY l.account_id, e.period_id, e.entry_type
WITH DATA;

-- Required by REFRESH MATERIALIZED VIEW CONCURRENTLY.
CREATE UNIQUE INDEX ux_mv_account_period_balances
    ON mv_account_period_balances (account_id, period_id, entry_type);
