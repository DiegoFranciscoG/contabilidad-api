package io.github.diegofranciscog.accounting.domain;

import io.github.diegofranciscog.accounting.exception.DomainException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static io.github.diegofranciscog.accounting.domain.TestChart.CASH;
import static io.github.diegofranciscog.accounting.domain.TestChart.EXPENSES;
import static io.github.diegofranciscog.accounting.domain.TestChart.SALES;
import static io.github.diegofranciscog.accounting.domain.TestChart.credit;
import static io.github.diegofranciscog.accounting.domain.TestChart.debit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JournalEntryTest {

    private static final Instant NOW = Instant.parse("2026-03-10T15:00:00Z");
    private final FiscalPeriod march = TestChart.openPeriod(2026, 3);

    private JournalEntry sale() {
        EntryHeader header = EntryHeader.manual(march, LocalDate.of(2026, 3, 10), "Venta al contado", EntryType.STANDARD);
        return JournalEntry.draft(header, List.of(debit(CASH, "100"), credit(SALES, "100")), "contador@demo.ec", NOW);
    }

    @Test
    void draftKeepsTotalsAndNumbersLines() {
        JournalEntry entry = sale();

        assertThat(entry.getStatus()).isEqualTo(EntryStatus.DRAFT);
        assertThat(entry.getTotalDebit()).isEqualByComparingTo("100.00");
        assertThat(entry.getTotalCredit()).isEqualByComparingTo("100.00");
        assertThat(entry.getLines()).extracting(JournalLine::getLineNumber).containsExactly(1, 2);
        assertThat(entry.getFiscalYear()).isEqualTo(2026);
    }

    @Test
    void postAssignsNumberAndBecomesImmutable() {
        JournalEntry entry = sale();
        entry.post(7, NOW);

        assertThat(entry.isPosted()).isTrue();
        assertThat(entry.getEntryNumber()).isEqualTo(7);
        assertThatThrownBy(() -> entry.update(march, LocalDate.of(2026, 3, 11), "otra", List.of(debit(CASH, "1"), credit(SALES, "1"))))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("reverso");
        assertThatThrownBy(() -> entry.post(8, NOW)).isInstanceOf(DomainException.class);
    }

    @Test
    void reversalMirrorsEveryLine() {
        JournalEntry entry = sale();
        entry.post(1, NOW);
        FiscalPeriod april = TestChart.openPeriod(2026, 4);

        JournalEntry reversal = entry.reversal(april, LocalDate.of(2026, 4, 2), "error de cliente", "contador@demo.ec", NOW);

        assertThat(reversal.getEntryType()).isEqualTo(EntryType.REVERSAL);
        assertThat(reversal.getReversesEntry()).isSameAs(entry);
        assertThat(reversal.getDescription()).isEqualTo("Reverso del asiento 2026-1: error de cliente");
        assertThat(reversal.getLines().get(0).credit()).isEqualByComparingTo("100");
        assertThat(reversal.getLines().get(1).debit()).isEqualByComparingTo("100");
    }

    @Test
    void draftCannotBeReversed() {
        assertThatThrownBy(() -> sale().reversal(march, LocalDate.of(2026, 3, 12), null, "x", NOW))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("contabilizados");
    }

    @Test
    void reversalOfReversalIsRejected() {
        JournalEntry entry = sale();
        entry.post(1, NOW);
        JournalEntry reversal = entry.reversal(march, LocalDate.of(2026, 3, 12), null, "x", NOW);
        reversal.post(2, NOW);

        assertThatThrownBy(() -> reversal.reversal(march, LocalDate.of(2026, 3, 13), null, "x", NOW))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void closedPeriodRejectsNewEntries() {
        march.close("admin@demo.ec", NOW);

        assertThatThrownBy(this::sale)
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("cerrado");
    }

    @Test
    void dateMustBelongToPeriod() {
        EntryHeader header = EntryHeader.manual(march, LocalDate.of(2026, 4, 1), "fuera", EntryType.STANDARD);

        assertThatThrownBy(() -> JournalEntry.draft(header, List.of(debit(CASH, "1"), credit(SALES, "1")), "x", NOW))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("no pertenece");
    }

    @Test
    void groupAccountsDoNotAcceptEntries() {
        EntryHeader header = EntryHeader.manual(march, LocalDate.of(2026, 3, 1), "grupo", EntryType.STANDARD);

        assertThatThrownBy(() -> JournalEntry.draft(header, List.of(debit(EXPENSES, "1"), credit(SALES, "1")), "x", NOW))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("de grupo");
    }

    @Test
    void descriptionIsRequired() {
        EntryHeader header = EntryHeader.manual(march, LocalDate.of(2026, 3, 1), " ", EntryType.STANDARD);

        assertThatThrownBy(() -> JournalEntry.draft(header, List.of(debit(CASH, "1"), credit(SALES, "1")), "x", NOW))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("glosa");
    }

    @Test
    void reversalTypeCannotBeCreatedDirectly() {
        EntryHeader header = EntryHeader.manual(march, LocalDate.of(2026, 3, 1), "manual", EntryType.REVERSAL);

        assertThatThrownBy(() -> JournalEntry.draft(header, List.of(debit(CASH, "1"), credit(SALES, "1")), "x", NOW))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void periodKnowsItsRangeAndCloses() {
        assertThat(march.contains(LocalDate.of(2026, 3, 31))).isTrue();
        assertThat(march.contains(LocalDate.of(2026, 2, 28))).isFalse();
        assertThat(march.getEndDate()).isEqualTo(LocalDate.of(2026, 3, 31));

        march.close("admin@demo.ec", NOW);

        assertThat(march.getStatus()).isEqualTo(PeriodStatus.CLOSED);
        assertThat(march.getClosedBy()).isEqualTo("admin@demo.ec");
        assertThatThrownBy(() -> march.close("otro", NOW)).isInstanceOf(DomainException.class);
    }
}
