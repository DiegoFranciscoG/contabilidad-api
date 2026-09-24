package io.github.diegofranciscog.accounting.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Gap-free numbering per fiscal year. The upsert locks the year's row until the transaction ends,
 * so a rolled-back posting releases its number and concurrent postings are serialised.
 */
@Repository
public class EntryNumberRepository {

    private static final String NEXT_NUMBER = """
            INSERT INTO entry_sequences (fiscal_year, last_number) VALUES (?, 1)
            ON CONFLICT (fiscal_year) DO UPDATE SET last_number = entry_sequences.last_number + 1
            RETURNING last_number
            """;

    private final JdbcTemplate jdbc;

    public EntryNumberRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int nextNumber(int fiscalYear) {
        Integer next = jdbc.queryForObject(NEXT_NUMBER, Integer.class, (short) fiscalYear);
        if (next == null) {
            throw new IllegalStateException("No se pudo asignar el número de asiento");
        }
        return next;
    }
}
