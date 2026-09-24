package io.github.diegofranciscog.accounting.repository;

import io.github.diegofranciscog.accounting.domain.EntrySource;
import io.github.diegofranciscog.accounting.domain.EntryStatus;
import io.github.diegofranciscog.accounting.domain.JournalEntry;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long>, JpaSpecificationExecutor<JournalEntry> {

    @EntityGraph(attributePaths = {"period", "lines", "lines.account", "lines.costCenter", "reversesEntry"})
    @Query("select e from JournalEntry e where e.id = :id")
    Optional<JournalEntry> findDetailedById(@Param("id") Long id);

    Optional<JournalEntry> findBySourceAndSourceReference(EntrySource source, String sourceReference);

    boolean existsByPeriodIdAndStatus(Long periodId, EntryStatus status);

    @Query("select e.id from JournalEntry e where e.reversesEntry.id = :id")
    Optional<Long> findReversalIdOf(@Param("id") Long id);

    @EntityGraph(attributePaths = {"period", "lines", "lines.account", "lines.costCenter", "reversesEntry"})
    @Query("""
            select distinct e from JournalEntry e
            where e.status = io.github.diegofranciscog.accounting.domain.EntryStatus.POSTED
              and e.entryDate between :from and :to
            order by e.entryDate, e.fiscalYear, e.entryNumber
            """)
    List<JournalEntry> findPostedBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
