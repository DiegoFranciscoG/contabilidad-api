package io.github.diegofranciscog.accounting.service.integration;

import io.github.diegofranciscog.accounting.AbstractIntegrationTest;
import io.github.diegofranciscog.accounting.domain.EntrySource;
import io.github.diegofranciscog.accounting.domain.EventType;
import io.github.diegofranciscog.accounting.dto.IntegrationResult;
import io.github.diegofranciscog.accounting.dto.InvoiceIssuedEvent;
import io.github.diegofranciscog.accounting.dto.JournalEntryResponse;
import io.github.diegofranciscog.accounting.dto.JournalLineResponse;
import io.github.diegofranciscog.accounting.dto.PayrollAccruedEvent;
import io.github.diegofranciscog.accounting.dto.RevenueKind;
import io.github.diegofranciscog.accounting.exception.DomainException;
import io.github.diegofranciscog.accounting.exception.NotFoundException;
import io.github.diegofranciscog.accounting.service.FiscalPeriodService;
import io.github.diegofranciscog.accounting.service.JournalEntryService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IntegrationEventServiceIT extends AbstractIntegrationTest {

    private static final int YEAR = 2044;

    @Autowired
    private IntegrationEventService service;

    @Autowired
    private EventAccountMappingService mappings;

    @Autowired
    private JournalEntryService entries;

    @Autowired
    private FiscalPeriodService periods;

    @BeforeAll
    void openYear() {
        periods.openYear(YEAR);
    }

    @Test
    void invoiceBecomesPostedEntryAndRetriesAreIdempotent() {
        InvoiceIssuedEvent invoice = new InvoiceIssuedEvent("001-001-000000123", LocalDate.of(YEAR, 3, 10),
                RevenueKind.GOODS, new BigDecimal("200.00"), new BigDecimal("30.00"), new BigDecimal("230.00"), null);

        IntegrationResult first = service.invoiceIssued(invoice);
        IntegrationResult retry = service.invoiceIssued(invoice);

        assertThat(first.created()).isTrue();
        assertThat(retry.created()).isFalse();
        assertThat(retry.entryId()).isEqualTo(first.entryId());

        JournalEntryResponse entry = entries.get(first.entryId());
        assertThat(entry.source()).isEqualTo(EntrySource.INVOICING);
        assertThat(entry.sourceReference()).isEqualTo("001-001-000000123");
        assertThat(entry.lines()).extracting(JournalLineResponse::accountCode)
                .containsExactly("101020502", "4101", "201070101");
        assertThat(entry.totalDebit()).isEqualByComparingTo("230.00");
    }

    @Test
    void serviceInvoiceWithoutVatUsesServiceRevenue() {
        IntegrationResult result = service.invoiceIssued(new InvoiceIssuedEvent("001-001-000000124",
                LocalDate.of(YEAR, 3, 11), RevenueKind.SERVICES, new BigDecimal("50.00"), BigDecimal.ZERO,
                new BigDecimal("50.00"), null));

        assertThat(entries.get(result.entryId()).lines()).extracting(JournalLineResponse::accountCode)
                .containsExactly("101020502", "4102");
    }

    @Test
    void invoiceThatDoesNotAddUpIsRejected() {
        InvoiceIssuedEvent wrong = new InvoiceIssuedEvent("001-001-000000999", LocalDate.of(YEAR, 3, 10),
                RevenueKind.GOODS, new BigDecimal("200.00"), new BigDecimal("30.00"), new BigDecimal("231.00"), null);

        assertThatThrownBy(() -> service.invoiceIssued(wrong))
                .isInstanceOf(DomainException.class).hasMessageContaining("no cuadra");
    }

    @Test
    void payrollAccrualSplitsPayables() {
        // Gross 2,000; employee 9.45 % = 189; employer 11.15 % = 223; benefits provision 350.
        PayrollAccruedEvent payroll = new PayrollAccruedEvent("NOM-2044-04", LocalDate.of(YEAR, 4, 30),
                new BigDecimal("2000.00"), new BigDecimal("189.00"), new BigDecimal("223.00"),
                new BigDecimal("350.00"), null);

        JournalEntryResponse entry = entries.get(service.payrollAccrued(payroll).entryId());

        assertThat(entry.source()).isEqualTo(EntrySource.PAYROLL);
        assertThat(entry.totalDebit()).isEqualByComparingTo("2573.00");
        assertThat(line(entry, "201070401").credit()).isEqualByComparingTo("1811.00");
        assertThat(line(entry, "2010703").credit()).isEqualByComparingTo("412.00");
        assertThat(line(entry, "201070402").credit()).isEqualByComparingTo("350.00");
    }

    @Test
    void payrollWithUnknownCostCenterOrWrongAmountsIsRejected() {
        PayrollAccruedEvent unknownCenter = new PayrollAccruedEvent("NOM-X", LocalDate.of(YEAR, 5, 31),
                new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "NO-EXISTE");
        PayrollAccruedEvent contributionTooHigh = new PayrollAccruedEvent("NOM-Y", LocalDate.of(YEAR, 5, 31),
                new BigDecimal("100.00"), new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.ZERO, null);

        assertThatThrownBy(() -> service.payrollAccrued(unknownCenter)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> service.payrollAccrued(contributionTooHigh)).isInstanceOf(DomainException.class);
    }

    @Test
    void mappingsCanPointToAnotherDetailAccount() {
        assertThat(mappings.list()).hasSize(10);

        var updated = mappings.update(EventType.INVOICE_ISSUED, "RECEIVABLE", "101020501");
        assertThat(updated.accountCode()).isEqualTo("101020501");
        mappings.update(EventType.INVOICE_ISSUED, "RECEIVABLE", "101020502");

        assertThatThrownBy(() -> mappings.update(EventType.INVOICE_ISSUED, "RECEIVABLE", "101"))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> mappings.update(EventType.INVOICE_ISSUED, "NOPE", "4101"))
                .isInstanceOf(NotFoundException.class);
    }

    private static JournalLineResponse line(JournalEntryResponse entry, String code) {
        return entry.lines().stream().filter(line -> line.accountCode().equals(code)).findFirst().orElseThrow();
    }
}
