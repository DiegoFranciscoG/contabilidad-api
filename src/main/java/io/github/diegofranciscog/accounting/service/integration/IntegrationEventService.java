package io.github.diegofranciscog.accounting.service.integration;

import io.github.diegofranciscog.accounting.domain.Account;
import io.github.diegofranciscog.accounting.domain.CostCenter;
import io.github.diegofranciscog.accounting.domain.EntryHeader;
import io.github.diegofranciscog.accounting.domain.EntrySource;
import io.github.diegofranciscog.accounting.domain.EntryType;
import io.github.diegofranciscog.accounting.domain.EventAccountMapping;
import io.github.diegofranciscog.accounting.domain.EventType;
import io.github.diegofranciscog.accounting.domain.FiscalPeriod;
import io.github.diegofranciscog.accounting.domain.JournalEntry;
import io.github.diegofranciscog.accounting.domain.LineSpec;
import io.github.diegofranciscog.accounting.domain.Money;
import io.github.diegofranciscog.accounting.dto.IntegrationResult;
import io.github.diegofranciscog.accounting.dto.InvoiceIssuedEvent;
import io.github.diegofranciscog.accounting.dto.PayrollAccruedEvent;
import io.github.diegofranciscog.accounting.dto.RevenueKind;
import io.github.diegofranciscog.accounting.exception.DomainException;
import io.github.diegofranciscog.accounting.mapper.JournalEntryMapper;
import io.github.diegofranciscog.accounting.repository.CostCenterRepository;
import io.github.diegofranciscog.accounting.repository.EventAccountMappingRepository;
import io.github.diegofranciscog.accounting.repository.FiscalPeriodRepository;
import io.github.diegofranciscog.accounting.repository.JournalEntryRepository;
import io.github.diegofranciscog.accounting.service.JournalEntryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Turns business events from facturador-sri and nomina-ec into posted entries. Each event is identified by
 * its external id, so a retried delivery returns the existing entry instead of duplicating it.
 */
@Service
public class IntegrationEventService {

    static final String SYSTEM_USER = "integration";

    private final JournalEntryService entryService;
    private final JournalEntryRepository entries;
    private final EventAccountMappingRepository mappings;
    private final FiscalPeriodRepository periods;
    private final CostCenterRepository costCenters;

    public IntegrationEventService(JournalEntryService entryService, JournalEntryRepository entries,
                                   EventAccountMappingRepository mappings, FiscalPeriodRepository periods,
                                   CostCenterRepository costCenters) {
        this.entryService = entryService;
        this.entries = entries;
        this.mappings = mappings;
        this.periods = periods;
        this.costCenters = costCenters;
    }

    /** Receivable (total) = revenue (subtotal) + VAT payable (vat). */
    @Transactional
    public IntegrationResult invoiceIssued(InvoiceIssuedEvent event) {
        Optional<IntegrationResult> existing = existing(EntrySource.INVOICING, event.invoiceId());
        if (existing.isPresent()) {
            return existing.get();
        }
        BigDecimal subtotal = Money.of(event.subtotal());
        BigDecimal vat = Money.of(event.vat());
        BigDecimal total = Money.of(event.total());
        if (subtotal.add(vat).compareTo(total) != 0) {
            throw new DomainException("La factura no cuadra: subtotal + IVA debe ser igual al total");
        }
        Map<String, Account> accounts = accountsFor(EventType.INVOICE_ISSUED);
        CostCenter costCenter = costCenter(event.costCenterCode());
        String revenueRole = event.kind() == RevenueKind.GOODS ? "SALES_REVENUE" : "SERVICE_REVENUE";

        List<LineSpec> lines = new ArrayList<>();
        lines.add(LineSpec.debit(role(accounts, "RECEIVABLE"), total, null, "Factura " + event.invoiceId()));
        lines.add(LineSpec.credit(role(accounts, revenueRole), subtotal, costCenter, null));
        if (vat.signum() > 0) {
            lines.add(LineSpec.credit(role(accounts, "VAT_PAYABLE"), vat, null, null));
        }
        return post(EntrySource.INVOICING, event.invoiceId(), event.issueDate(),
                "Factura emitida " + event.invoiceId(), lines);
    }

    /**
     * Payroll accrual: salary, employer contribution and benefits expenses against salaries payable
     * (gross − employee contribution), IESS payable (both contributions) and benefits payable.
     */
    @Transactional
    public IntegrationResult payrollAccrued(PayrollAccruedEvent event) {
        Optional<IntegrationResult> existing = existing(EntrySource.PAYROLL, event.payrollId());
        if (existing.isPresent()) {
            return existing.get();
        }
        BigDecimal gross = Money.of(event.grossSalaries());
        BigDecimal employee = Money.of(event.employeeContribution());
        BigDecimal employer = Money.of(event.employerContribution());
        BigDecimal benefits = Money.of(event.benefitsProvision());
        if (employee.compareTo(gross) >= 0) {
            throw new DomainException("El aporte personal debe ser menor que los sueldos brutos");
        }
        Map<String, Account> accounts = accountsFor(EventType.PAYROLL_ACCRUED);
        CostCenter costCenter = costCenter(event.costCenterCode());

        List<LineSpec> lines = new ArrayList<>();
        lines.add(LineSpec.debit(role(accounts, "SALARY_EXPENSE"), gross, costCenter, null));
        if (employer.signum() > 0) {
            lines.add(LineSpec.debit(role(accounts, "EMPLOYER_CONTRIBUTION_EXPENSE"), employer, costCenter, null));
        }
        if (benefits.signum() > 0) {
            lines.add(LineSpec.debit(role(accounts, "BENEFITS_EXPENSE"), benefits, costCenter, null));
        }
        lines.add(LineSpec.credit(role(accounts, "SALARIES_PAYABLE"), gross.subtract(employee), null, null));
        BigDecimal iess = employee.add(employer);
        if (iess.signum() > 0) {
            lines.add(LineSpec.credit(role(accounts, "IESS_PAYABLE"), iess, null, null));
        }
        if (benefits.signum() > 0) {
            lines.add(LineSpec.credit(role(accounts, "BENEFITS_PAYABLE"), benefits, null, null));
        }
        return post(EntrySource.PAYROLL, event.payrollId(), event.accrualDate(),
                "Provisión de nómina " + event.payrollId(), lines);
    }

    private IntegrationResult post(EntrySource source, String reference, LocalDate date, String description,
                                   List<LineSpec> lines) {
        FiscalPeriod period = periods.findByDate(date)
                .orElseThrow(() -> new DomainException("No existe un periodo contable para la fecha " + date));
        EntryHeader header = new EntryHeader(period, date, description, EntryType.STANDARD, source, reference);
        JournalEntry entry = entryService.createPosted(header, lines, SYSTEM_USER);
        return new IntegrationResult(entry.getId(), JournalEntryMapper.number(entry.getFiscalYear(), entry.getEntryNumber()), true);
    }

    private Optional<IntegrationResult> existing(EntrySource source, String reference) {
        return entries.findBySourceAndSourceReference(source, reference)
                .map(entry -> new IntegrationResult(entry.getId(),
                        JournalEntryMapper.number(entry.getFiscalYear(), entry.getEntryNumber()), false));
    }

    private Map<String, Account> accountsFor(EventType type) {
        return mappings.findByEventType(type).stream()
                .collect(Collectors.toMap(EventAccountMapping::getAccountRole, EventAccountMapping::getAccount,
                        (first, second) -> first));
    }

    private static Account role(Map<String, Account> accounts, String role) {
        return Optional.ofNullable(accounts.get(role))
                .orElseThrow(() -> new DomainException("Falta configurar la cuenta para " + role));
    }

    private CostCenter costCenter(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return costCenters.findByCode(code)
                .orElseThrow(() -> new DomainException("No existe el centro de costo " + code));
    }
}
