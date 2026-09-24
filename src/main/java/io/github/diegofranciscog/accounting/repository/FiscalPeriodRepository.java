package io.github.diegofranciscog.accounting.repository;

import io.github.diegofranciscog.accounting.domain.FiscalPeriod;
import io.github.diegofranciscog.accounting.domain.PeriodStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FiscalPeriodRepository extends JpaRepository<FiscalPeriod, Long> {

    Optional<FiscalPeriod> findByYearAndMonth(short year, short month);

    List<FiscalPeriod> findByYearOrderByMonth(short year);

    List<FiscalPeriod> findAllByOrderByYearAscMonthAsc();

    boolean existsByYear(short year);

    @Query("select p from FiscalPeriod p where :date between p.startDate and p.endDate")
    Optional<FiscalPeriod> findByDate(@Param("date") LocalDate date);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from FiscalPeriod p where p.id = :id")
    Optional<FiscalPeriod> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select count(p) > 0 from FiscalPeriod p
            where p.status = :status and p.year = :year and p.month < :month
            """)
    boolean existsEarlierInYearWithStatus(@Param("year") short year, @Param("month") short month,
                                    @Param("status") PeriodStatus status);
}
