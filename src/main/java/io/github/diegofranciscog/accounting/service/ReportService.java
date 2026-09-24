package io.github.diegofranciscog.accounting.service;

import io.github.diegofranciscog.accounting.domain.Account;
import io.github.diegofranciscog.accounting.domain.AccountMovement;
import io.github.diegofranciscog.accounting.domain.EntryType;
import io.github.diegofranciscog.accounting.domain.JournalEntry;
import io.github.diegofranciscog.accounting.domain.Money;
import io.github.diegofranciscog.accounting.dto.JournalEntryResponse;
import io.github.diegofranciscog.accounting.dto.report.BalanceSheetReport;
import io.github.diegofranciscog.accounting.dto.report.IncomeStatementReport;
import io.github.diegofranciscog.accounting.dto.report.JournalReport;
import io.github.diegofranciscog.accounting.dto.report.LedgerReport;
import io.github.diegofranciscog.accounting.dto.report.TrialBalanceReport;
import io.github.diegofranciscog.accounting.exception.DomainException;
import io.github.diegofranciscog.accounting.exception.NotFoundException;
import io.github.diegofranciscog.accounting.mapper.JournalEntryMapper;
import io.github.diegofranciscog.accounting.repository.AccountRepository;
import io.github.diegofranciscog.accounting.repository.JournalEntryRepository;
import io.github.diegofranciscog.accounting.repository.LedgerQueryRepository;
import io.github.diegofranciscog.accounting.service.report.AccountNode;
import io.github.diegofranciscog.accounting.service.report.BalanceSheetBuilder;
import io.github.diegofranciscog.accounting.service.report.IncomeStatementBuilder;
import io.github.diegofranciscog.accounting.service.report.TrialBalanceBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Journal, ledger, trial balance and financial statements. Balances come from the materialized view. */
@Service
@Transactional(readOnly = true)
public class ReportService {

    static final long MAX_RANGE_DAYS = 366;
    private static final int MIN_KEY = 0;

    private final AccountRepository accounts;
    private final JournalEntryRepository entries;
    private final LedgerQueryRepository ledger;
    private final CostCenterService costCenterService;
    private final JournalEntryMapper mapper;

    public ReportService(AccountRepository accounts, JournalEntryRepository entries, LedgerQueryRepository ledger,
                         CostCenterService costCenterService, JournalEntryMapper mapper) {
        this.accounts = accounts;
        this.entries = entries;
        this.ledger = ledger;
        this.costCenterService = costCenterService;
        this.mapper = mapper;
    }

    /** Libro diario. */
    public JournalReport journal(LocalDate from, LocalDate to) {
        requireRange(from, to);
        List<JournalEntry> posted = entries.findPostedBetween(from, to);
        BigDecimal total = Money.ZERO;
        List<JournalEntryResponse> rows = new ArrayList<>(posted.size());
        for (JournalEntry entry : posted) {
            rows.add(mapper.toResponse(entry, null));
            total = total.add(entry.getTotalDebit());
        }
        return new JournalReport(from, to, rows, total, total);
    }

    /** Libro mayor of an account; a group account includes all its sub-accounts. */
    public LedgerReport ledger(String accountCode, LocalDate from, LocalDate to) {
        requireRange(from, to);
        Account account = accounts.findByCode(accountCode)
                .orElseThrow(() -> new NotFoundException("Cuenta", accountCode));
        List<Long> ids = accounts.findAllWithParentOrderByCode().stream()
                .filter(candidate -> candidate.getCode().startsWith(accountCode))
                .map(Account::getId)
                .toList();

        AccountMovement opening = ledger.openingFor(ids, from);
        BigDecimal balance = account.balance(opening.debit(), opening.credit());
        BigDecimal openingBalance = balance;
        BigDecimal totalDebit = Money.ZERO;
        BigDecimal totalCredit = Money.ZERO;
        List<LedgerReport.Line> lines = new ArrayList<>();
        for (LedgerQueryRepository.LedgerRow row : ledger.ledgerLines(ids, from, to)) {
            balance = balance.add(account.balance(row.debit(), row.credit()));
            totalDebit = totalDebit.add(row.debit());
            totalCredit = totalCredit.add(row.credit());
            String description = row.lineDescription() == null ? row.entryDescription() : row.lineDescription();
            lines.add(new LedgerReport.Line(row.date(), JournalEntryMapper.number(row.fiscalYear(), row.entryNumber()),
                    row.entryId(), row.accountCode(), description, row.costCenterCode(), row.debit(), row.credit(), balance));
        }
        return new LedgerReport(account.getCode(), account.getName(), account.getNature(), from, to, openingBalance,
                lines, totalDebit, totalCredit, balance);
    }

    /** Balance de comprobación for months {@code fromMonth..toMonth} of {@code year}. */
    public TrialBalanceReport trialBalance(int year, int fromMonth, int toMonth) {
        requireMonths(year, fromMonth, toMonth);
        int fromKey = key(year, fromMonth);
        Map<Long, AccountMovement> opening = ledger.balancesByPeriod(MIN_KEY, fromKey - 1, List.of());
        Map<Long, AccountMovement> period = ledger.balancesByPeriod(fromKey, key(year, toMonth), List.of());
        return TrialBalanceBuilder.build(year, fromMonth, toMonth, nodes(), opening, period);
    }

    /** Estado de situación financiera at the end of {@code month}. */
    public BalanceSheetReport balanceSheet(int year, int month, int level) {
        requireMonths(year, month, month);
        Map<Long, AccountMovement> cumulative = ledger.balancesByPeriod(MIN_KEY, key(year, month), List.of());
        return BalanceSheetBuilder.build(YearMonth.of(year, month).atEndOfMonth(), nodes(), cumulative, level);
    }

    /** Estado de resultados; excludes the closing entry and can be filtered by cost center. */
    public IncomeStatementReport incomeStatement(int year, int fromMonth, int toMonth, String costCenterCode, int level) {
        requireMonths(year, fromMonth, toMonth);
        List<EntryType> excluded = List.of(EntryType.CLOSING);
        Map<Long, AccountMovement> movements = costCenterCode == null || costCenterCode.isBlank()
                ? ledger.balancesByPeriod(key(year, fromMonth), key(year, toMonth), excluded)
                : ledger.movementsByCostCenter(key(year, fromMonth), key(year, toMonth), excluded,
                costCenterService.require(costCenterCode).getId());
        return IncomeStatementBuilder.build(year, fromMonth, toMonth, costCenterCode, nodes(), movements, level);
    }

    private List<AccountNode> nodes() {
        return accounts.findAllWithParentOrderByCode().stream().map(AccountNode::of).toList();
    }

    private static int key(int year, int month) {
        return year * 100 + month;
    }

    private static void requireRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new DomainException("Rango de fechas inválido");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw new DomainException("El rango máximo es de " + MAX_RANGE_DAYS + " días");
        }
    }

    private static void requireMonths(int year, int fromMonth, int toMonth) {
        if (year < 2000 || year > 2100 || fromMonth < 1 || toMonth > 12 || fromMonth > toMonth) {
            throw new DomainException("Periodo inválido: año 2000-2100 y meses 1-12 en orden");
        }
    }
}
