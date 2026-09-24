package io.github.diegofranciscog.accounting.service;

import io.github.diegofranciscog.accounting.AbstractIntegrationTest;
import io.github.diegofranciscog.accounting.domain.PeriodStatus;
import io.github.diegofranciscog.accounting.dto.FiscalPeriodResponse;
import io.github.diegofranciscog.accounting.dto.JournalEntryResponse;
import io.github.diegofranciscog.accounting.dto.ReverseEntryRequest;
import io.github.diegofranciscog.accounting.exception.ConflictException;
import io.github.diegofranciscog.accounting.exception.DomainException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FiscalPeriodServiceIT extends AbstractIntegrationTest {

    private static final String ADMIN = "admin@demo.ec";

    @Autowired
    private FiscalPeriodService periods;

    @Autowired
    private JournalEntryService entries;

    @Test
    void openYearCreatesTwelveOpenMonthsOnce() {
        List<FiscalPeriodResponse> created = periods.openYear(2043);

        assertThat(created).hasSize(12).allMatch(period -> period.status() == PeriodStatus.OPEN);
        assertThat(created.get(1).endDate()).isEqualTo(LocalDate.of(2043, 2, 28));
        assertThat(periods.list(2043)).hasSize(12);
        assertThatThrownBy(() -> periods.openYear(2043)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> periods.openYear(1999)).isInstanceOf(DomainException.class);
    }

    @Test
    void periodsCloseInOrderAndWithoutDrafts() {
        periods.openYear(2046);
        JournalEntryResponse draft = entries.create(JournalEntryServiceIT.sale(LocalDate.of(2046, 1, 10), "20.00", false), ADMIN);

        assertThatThrownBy(() -> periods.close(2046, 2, ADMIN))
                .isInstanceOf(DomainException.class).hasMessageContaining("anteriores");
        assertThatThrownBy(() -> periods.close(2046, 1, ADMIN))
                .isInstanceOf(DomainException.class).hasMessageContaining("borradores");

        entries.post(draft.id());
        FiscalPeriodResponse closed = periods.close(2046, 1, ADMIN);

        assertThat(closed.status()).isEqualTo(PeriodStatus.CLOSED);
        assertThat(closed.closedBy()).isEqualTo(ADMIN);
        assertThatThrownBy(() -> periods.close(2046, 1, ADMIN)).isInstanceOf(DomainException.class);
    }

    @Test
    void closedPeriodIsCorrectedWithReversalInOpenPeriod() {
        periods.openYear(2047);
        JournalEntryResponse posted = entries.create(JournalEntryServiceIT.sale(LocalDate.of(2047, 1, 10), "70.00", true), ADMIN);
        periods.close(2047, 1, ADMIN);

        assertThatThrownBy(() -> entries.create(JournalEntryServiceIT.sale(LocalDate.of(2047, 1, 11), "1.00", true), ADMIN))
                .isInstanceOf(DomainException.class).hasMessageContaining("cerrado");
        assertThatThrownBy(() -> entries.reverse(posted.id(), new ReverseEntryRequest(LocalDate.of(2047, 1, 20), null), ADMIN))
                .isInstanceOf(DomainException.class).hasMessageContaining("cerrado");

        JournalEntryResponse reversal = entries.reverse(posted.id(), new ReverseEntryRequest(LocalDate.of(2047, 2, 1), "Error"), ADMIN);

        assertThat(reversal.period()).isEqualTo("2047-02");
    }
}
