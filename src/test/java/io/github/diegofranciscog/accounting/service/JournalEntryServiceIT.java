package io.github.diegofranciscog.accounting.service;

import io.github.diegofranciscog.accounting.AbstractIntegrationTest;
import io.github.diegofranciscog.accounting.domain.EntryStatus;
import io.github.diegofranciscog.accounting.domain.EntryType;
import io.github.diegofranciscog.accounting.dto.JournalEntryRequest;
import io.github.diegofranciscog.accounting.dto.JournalEntryResponse;
import io.github.diegofranciscog.accounting.dto.JournalLineRequest;
import io.github.diegofranciscog.accounting.dto.ReverseEntryRequest;
import io.github.diegofranciscog.accounting.exception.ConflictException;
import io.github.diegofranciscog.accounting.exception.DomainException;
import io.github.diegofranciscog.accounting.exception.NotFoundException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JournalEntryServiceIT extends AbstractIntegrationTest {

    private static final int YEAR = 2041;
    private static final String USER = "contador@demo.ec";

    @Autowired
    private JournalEntryService service;

    @Autowired
    private FiscalPeriodService periods;

    @BeforeAll
    void openYear() {
        periods.openYear(YEAR);
    }

    @Test
    void draftIsEditableAndPostingAssignsConsecutiveNumbers() {
        JournalEntryResponse draft = service.create(sale(LocalDate.of(YEAR, 2, 3), "100.00", false), USER);
        assertThat(draft.status()).isEqualTo(EntryStatus.DRAFT);
        assertThat(draft.number()).isNull();

        JournalEntryResponse edited = service.update(draft.id(), sale(LocalDate.of(YEAR, 2, 4), "250.00", false));
        assertThat(edited.totalDebit()).isEqualByComparingTo("250.00");
        assertThat(edited.lines()).hasSize(2);

        JournalEntryResponse first = service.post(draft.id());
        JournalEntryResponse second = service.create(sale(LocalDate.of(YEAR, 2, 5), "10.00", true), USER);

        int firstNumber = Integer.parseInt(first.number().split("-")[1]);
        assertThat(first.status()).isEqualTo(EntryStatus.POSTED);
        assertThat(second.number()).isEqualTo(YEAR + "-" + (firstNumber + 1));
    }

    @Test
    void postedEntryCannotBeEditedOrDeleted() {
        JournalEntryResponse posted = service.create(sale(LocalDate.of(YEAR, 3, 1), "40.00", true), USER);

        assertThatThrownBy(() -> service.update(posted.id(), sale(LocalDate.of(YEAR, 3, 2), "41.00", false)))
                .isInstanceOf(DomainException.class).hasMessageContaining("reverso");
        assertThatThrownBy(() -> service.deleteDraft(posted.id()))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void draftCanBeDeleted() {
        JournalEntryResponse draft = service.create(sale(LocalDate.of(YEAR, 3, 5), "5.00", false), USER);

        service.deleteDraft(draft.id());

        assertThatThrownBy(() -> service.get(draft.id())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void unbalancedRequestIsRejected() {
        JournalEntryRequest request = new JournalEntryRequest(LocalDate.of(YEAR, 4, 1), "Descuadrado", null, List.of(
                line("1010101", "100.00", null), line("4101", null, "99.00")), true);

        assertThatThrownBy(() -> service.create(request, USER))
                .isInstanceOf(DomainException.class).hasMessageContaining("no cuadra");
    }

    @Test
    void groupAccountIsRejected() {
        JournalEntryRequest request = new JournalEntryRequest(LocalDate.of(YEAR, 4, 1), "Cuenta de grupo", null, List.of(
                line("101", "10.00", null), line("4101", null, "10.00")), true);

        assertThatThrownBy(() -> service.create(request, USER))
                .isInstanceOf(DomainException.class).hasMessageContaining("de grupo");
    }

    @Test
    void unknownAccountIsNotFound() {
        JournalEntryRequest request = new JournalEntryRequest(LocalDate.of(YEAR, 4, 1), "Cuenta inexistente", null, List.of(
                line("1999999", "10.00", null), line("4101", null, "10.00")), true);

        assertThatThrownBy(() -> service.create(request, USER)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void dateWithoutPeriodIsRejected() {
        assertThatThrownBy(() -> service.create(sale(LocalDate.of(2099, 1, 1), "1.00", true), USER))
                .isInstanceOf(DomainException.class).hasMessageContaining("No existe un periodo");
    }

    @Test
    void closingAndReversalTypesAreNotManual() {
        JournalEntryRequest closing = new JournalEntryRequest(LocalDate.of(YEAR, 4, 1), "Cierre manual", EntryType.CLOSING,
                List.of(line("1010101", "1.00", null), line("4101", null, "1.00")), true);

        assertThatThrownBy(() -> service.create(closing, USER)).isInstanceOf(DomainException.class);
    }

    @Test
    void reversalMirrorsOriginalAndOnlyOnce() {
        JournalEntryResponse original = service.create(sale(LocalDate.of(YEAR, 5, 10), "115.00", true), USER);

        JournalEntryResponse reversal = service.reverse(original.id(),
                new ReverseEntryRequest(LocalDate.of(YEAR, 5, 12), "Factura anulada"), USER);

        assertThat(reversal.type()).isEqualTo(EntryType.REVERSAL);
        assertThat(reversal.status()).isEqualTo(EntryStatus.POSTED);
        assertThat(reversal.reversesEntryId()).isEqualTo(original.id());
        assertThat(reversal.lines().get(0).credit()).isEqualByComparingTo("115.00");
        assertThat(service.get(original.id()).reversedByEntryId()).isEqualTo(reversal.id());
        assertThatThrownBy(() -> service.reverse(original.id(), new ReverseEntryRequest(LocalDate.of(YEAR, 5, 13), null), USER))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void reversalCannotPrecedeOriginal() {
        JournalEntryResponse original = service.create(sale(LocalDate.of(YEAR, 6, 10), "1.00", true), USER);

        assertThatThrownBy(() -> service.reverse(original.id(), new ReverseEntryRequest(LocalDate.of(YEAR, 6, 9), null), USER))
                .isInstanceOf(DomainException.class).hasMessageContaining("anterior");
    }

    @Test
    void searchFiltersByStatusAndDates() {
        service.create(sale(LocalDate.of(YEAR, 7, 1), "3.00", false), USER);

        var drafts = service.search(EntryStatus.DRAFT, null, LocalDate.of(YEAR, 7, 1), LocalDate.of(YEAR, 7, 31),
                PageRequest.of(0, 20));

        assertThat(drafts.getContent()).isNotEmpty().allMatch(summary -> summary.status() == EntryStatus.DRAFT);
    }

    static JournalEntryRequest sale(LocalDate date, String amount, boolean post) {
        return new JournalEntryRequest(date, "Venta de mercadería", null,
                List.of(line("1010101", amount, null), line("4101", null, amount)), post);
    }

    static JournalLineRequest line(String account, String debit, String credit) {
        return new JournalLineRequest(account, null, debit == null ? null : new BigDecimal(debit),
                credit == null ? null : new BigDecimal(credit), null);
    }
}
