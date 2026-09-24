package io.github.diegofranciscog.accounting.service;

import io.github.diegofranciscog.accounting.domain.Account;
import io.github.diegofranciscog.accounting.domain.AccountElement;
import io.github.diegofranciscog.accounting.domain.AccountMovement;
import io.github.diegofranciscog.accounting.domain.EntryHeader;
import io.github.diegofranciscog.accounting.domain.EntrySource;
import io.github.diegofranciscog.accounting.domain.EntryType;
import io.github.diegofranciscog.accounting.domain.FiscalPeriod;
import io.github.diegofranciscog.accounting.domain.JournalEntry;
import io.github.diegofranciscog.accounting.domain.LineSpec;
import io.github.diegofranciscog.accounting.domain.Money;
import io.github.diegofranciscog.accounting.dto.JournalEntryResponse;
import io.github.diegofranciscog.accounting.exception.DomainException;
import io.github.diegofranciscog.accounting.mapper.JournalEntryMapper;
import io.github.diegofranciscog.accounting.repository.AccountRepository;
import io.github.diegofranciscog.accounting.repository.LedgerQueryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Year-end closing (R10): one entry dated 31 December that brings every income and expense account to zero
 * and moves the net result to 30701 (profit) or 30702 (loss). Income statements exclude this entry.
 */
@Service
public class YearEndClosingService {

    static final String PROFIT_ACCOUNT = "30701";
    static final String LOSS_ACCOUNT = "30702";

    private final AccountRepository accounts;
    private final LedgerQueryRepository ledger;
    private final FiscalPeriodService periodService;
    private final JournalEntryService entryService;
    private final JournalEntryMapper mapper;

    public YearEndClosingService(AccountRepository accounts, LedgerQueryRepository ledger,
                                 FiscalPeriodService periodService, JournalEntryService entryService,
                                 JournalEntryMapper mapper) {
        this.accounts = accounts;
        this.ledger = ledger;
        this.periodService = periodService;
        this.entryService = entryService;
        this.mapper = mapper;
    }

    @Transactional
    public JournalEntryResponse close(int year, String user) {
        LocalDate closingDate = LocalDate.of(year, 12, 31);
        FiscalPeriod december = periodService.openPeriodFor(closingDate);
        Map<Long, AccountMovement> movements = ledger.liveBalancesForYear(year, List.of(EntryType.CLOSING));

        List<LineSpec> lines = new ArrayList<>();
        BigDecimal result = Money.ZERO;
        for (Account account : accounts.findAllById(movements.keySet())) {
            if (account.getElement() != AccountElement.INCOME && account.getElement() != AccountElement.EXPENSE) {
                continue;
            }
            BigDecimal net = movements.get(account.getId()).net();
            if (net.signum() > 0) {
                lines.add(LineSpec.credit(account, net, null, "Cierre"));
            } else if (net.signum() < 0) {
                lines.add(LineSpec.debit(account, net.negate(), null, "Cierre"));
            }
            result = result.subtract(net);
        }
        if (lines.isEmpty()) {
            throw new DomainException("El ejercicio " + year + " no tiene saldos en cuentas de resultados");
        }
        if (result.signum() > 0) {
            lines.add(LineSpec.credit(require(PROFIT_ACCOUNT), result, null, "Ganancia neta del ejercicio"));
        } else if (result.signum() < 0) {
            lines.add(LineSpec.debit(require(LOSS_ACCOUNT), result.negate(), null, "Pérdida neta del ejercicio"));
        }

        EntryHeader header = new EntryHeader(december, closingDate, "Cierre del ejercicio " + year,
                EntryType.CLOSING, EntrySource.SYSTEM, "YEAR-CLOSE-" + year);
        JournalEntry entry = entryService.createPosted(header, lines, user);
        return mapper.toResponse(entry, null);
    }

    private Account require(String code) {
        return accounts.findByCode(code).orElseThrow(() ->
                new DomainException("Falta la cuenta " + code + " (Supercias) necesaria para el cierre"));
    }
}
