package io.github.diegofranciscog.accounting.service;

import io.github.diegofranciscog.accounting.domain.EntryStatus;
import io.github.diegofranciscog.accounting.domain.FiscalPeriod;
import io.github.diegofranciscog.accounting.domain.PeriodStatus;
import io.github.diegofranciscog.accounting.dto.FiscalPeriodResponse;
import io.github.diegofranciscog.accounting.exception.ConflictException;
import io.github.diegofranciscog.accounting.exception.DomainException;
import io.github.diegofranciscog.accounting.exception.NotFoundException;
import io.github.diegofranciscog.accounting.mapper.AccountingMapper;
import io.github.diegofranciscog.accounting.repository.FiscalPeriodRepository;
import io.github.diegofranciscog.accounting.repository.JournalEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@Service
public class FiscalPeriodService {

    private final FiscalPeriodRepository periods;
    private final JournalEntryRepository entries;
    private final AccountingMapper mapper;
    private final Clock clock;

    public FiscalPeriodService(FiscalPeriodRepository periods, JournalEntryRepository entries, AccountingMapper mapper,
                               Clock clock) {
        this.periods = periods;
        this.entries = entries;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<FiscalPeriodResponse> list(Integer year) {
        List<FiscalPeriod> result = year == null
                ? periods.findAllByOrderByYearAscMonthAsc()
                : periods.findByYearOrderByMonth(year.shortValue());
        return result.stream().map(mapper::toResponse).toList();
    }

    /** Creates the twelve monthly periods of a fiscal year, all open. */
    @Transactional
    public List<FiscalPeriodResponse> openYear(int year) {
        if (year < 2000 || year > 2100) {
            throw new DomainException("El año debe estar entre 2000 y 2100");
        }
        if (periods.existsByYear((short) year)) {
            throw new ConflictException("El ejercicio " + year + " ya tiene periodos");
        }
        List<FiscalPeriodResponse> created = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            created.add(mapper.toResponse(periods.save(new FiscalPeriod(YearMonth.of(year, month)))));
        }
        return created;
    }

    /**
     * Closes a period. Months of a fiscal year close in order and never with drafts pending, so a closed month
     * is final and later corrections go through reversal entries (R6). December of the previous year may stay
     * open for adjustments while the new year runs.
     */
    @Transactional
    public FiscalPeriodResponse close(int year, int month, String user) {
        FiscalPeriod period = periods.findByYearAndMonth((short) year, (short) month)
                .flatMap(found -> periods.findByIdForUpdate(found.getId()))
                .orElseThrow(() -> new NotFoundException("Periodo", YearMonth.of(year, month)));
        period.requireOpen();
        if (periods.existsEarlierInYearWithStatus((short) year, (short) month, PeriodStatus.OPEN)) {
            throw new DomainException("Cierra primero los periodos anteriores a " + period.label());
        }
        if (entries.existsByPeriodIdAndStatus(period.getId(), EntryStatus.DRAFT)) {
            throw new DomainException("El periodo " + period.label() + " tiene borradores: contabilízalos o elimínalos");
        }
        period.close(user, clock.instant());
        return mapper.toResponse(period);
    }

    /** The open period that contains {@code date}. */
    FiscalPeriod openPeriodFor(LocalDate date) {
        FiscalPeriod period = periods.findByDate(date)
                .orElseThrow(() -> new DomainException("No existe un periodo contable para la fecha " + date
                        + "; abre el ejercicio " + date.getYear()));
        period.requireOpen();
        return period;
    }
}
