package io.github.diegofranciscog.accounting.service;

import io.github.diegofranciscog.accounting.domain.Account;
import io.github.diegofranciscog.accounting.domain.CostCenter;
import io.github.diegofranciscog.accounting.domain.EntryHeader;
import io.github.diegofranciscog.accounting.domain.EntrySource;
import io.github.diegofranciscog.accounting.domain.EntryStatus;
import io.github.diegofranciscog.accounting.domain.EntryType;
import io.github.diegofranciscog.accounting.domain.FiscalPeriod;
import io.github.diegofranciscog.accounting.domain.JournalEntry;
import io.github.diegofranciscog.accounting.domain.LineSpec;
import io.github.diegofranciscog.accounting.domain.Money;
import io.github.diegofranciscog.accounting.dto.JournalEntryRequest;
import io.github.diegofranciscog.accounting.dto.JournalEntryResponse;
import io.github.diegofranciscog.accounting.dto.JournalEntrySummary;
import io.github.diegofranciscog.accounting.dto.JournalLineRequest;
import io.github.diegofranciscog.accounting.dto.ReverseEntryRequest;
import io.github.diegofranciscog.accounting.exception.ConflictException;
import io.github.diegofranciscog.accounting.exception.DomainException;
import io.github.diegofranciscog.accounting.exception.NotFoundException;
import io.github.diegofranciscog.accounting.mapper.JournalEntryMapper;
import io.github.diegofranciscog.accounting.repository.EntryNumberRepository;
import io.github.diegofranciscog.accounting.repository.JournalEntryRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class JournalEntryService {

    private final JournalEntryRepository entries;
    private final EntryNumberRepository entryNumbers;
    private final AccountService accountService;
    private final CostCenterService costCenterService;
    private final FiscalPeriodService periodService;
    private final JournalEntryMapper mapper;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public JournalEntryService(JournalEntryRepository entries, EntryNumberRepository entryNumbers,
                               AccountService accountService, CostCenterService costCenterService,
                               FiscalPeriodService periodService, JournalEntryMapper mapper,
                               ApplicationEventPublisher events, Clock clock) {
        this.entries = entries;
        this.entryNumbers = entryNumbers;
        this.accountService = accountService;
        this.costCenterService = costCenterService;
        this.periodService = periodService;
        this.mapper = mapper;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public JournalEntryResponse create(JournalEntryRequest request, String user) {
        EntryType type = request.type() == null ? EntryType.STANDARD : request.type();
        if (type != EntryType.STANDARD && type != EntryType.OPENING) {
            throw new DomainException("Tipo no permitido: usa STANDARD u OPENING; cierre y reverso tienen su propia operación");
        }
        FiscalPeriod period = periodService.openPeriodFor(request.date());
        EntryHeader header = EntryHeader.manual(period, request.date(), request.description(), type);
        JournalEntry entry = entries.saveAndFlush(JournalEntry.draft(header, resolve(request.lines()), user, clock.instant()));
        if (request.post()) {
            postEntry(entry);
        }
        return mapper.toResponse(entry, null);
    }

    @Transactional
    public JournalEntryResponse update(Long id, JournalEntryRequest request) {
        JournalEntry entry = requireDetailed(id);
        entry.requireDraft();
        FiscalPeriod period = periodService.openPeriodFor(request.date());
        entry.update(period, request.date(), request.description(), resolve(request.lines()));
        entries.saveAndFlush(entry);
        if (request.post()) {
            postEntry(entry);
        }
        return mapper.toResponse(entry, null);
    }

    @Transactional
    public void deleteDraft(Long id) {
        JournalEntry entry = requireDetailed(id);
        entry.requireDraft();
        entry.getPeriod().requireOpen();
        entries.delete(entry);
    }

    @Transactional
    public JournalEntryResponse post(Long id) {
        JournalEntry entry = requireDetailed(id);
        postEntry(entry);
        return mapper.toResponse(entry, null);
    }

    /** Cancels a posted entry with its mirror image dated in an open period (R6). */
    @Transactional
    public JournalEntryResponse reverse(Long id, ReverseEntryRequest request, String user) {
        JournalEntry original = requireDetailed(id);
        if (entries.findReversalIdOf(id).isPresent()) {
            throw new ConflictException("El asiento ya fue reversado");
        }
        LocalDate date = request == null || request.date() == null ? LocalDate.now(clock) : request.date();
        if (date.isBefore(original.getEntryDate())) {
            throw new DomainException("El reverso no puede tener fecha anterior al asiento original (" + original.getEntryDate() + ")");
        }
        FiscalPeriod period = periodService.openPeriodFor(date);
        JournalEntry reversal = original.reversal(period, date, request == null ? null : request.reason(), user, clock.instant());
        entries.saveAndFlush(reversal);
        postEntry(reversal);
        return mapper.toResponse(reversal, null);
    }

    /** Creates and posts an entry in one step (automatic entries, year-end closing, demo data). */
    @Transactional
    public JournalEntry createPosted(EntryHeader header, List<LineSpec> lines, String user) {
        if (header.sourceReference() != null
                && entries.findBySourceAndSourceReference(header.source(), header.sourceReference()).isPresent()) {
            throw new ConflictException("Ya existe un asiento para " + header.source() + " " + header.sourceReference());
        }
        JournalEntry entry = entries.saveAndFlush(JournalEntry.draft(header, lines, user, clock.instant()));
        postEntry(entry);
        return entry;
    }

    @Transactional(readOnly = true)
    public JournalEntryResponse get(Long id) {
        JournalEntry entry = requireDetailed(id);
        return mapper.toResponse(entry, entries.findReversalIdOf(id).orElse(null));
    }

    @Transactional(readOnly = true)
    public Page<JournalEntrySummary> search(EntryStatus status, EntrySource source, LocalDate from, LocalDate to,
                                            Pageable pageable) {
        Specification<JournalEntry> spec = Specification.unrestricted();
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (source != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("source"), source));
        }
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("entryDate"), from));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("entryDate"), to));
        }
        return entries.findAll(spec, pageable).map(mapper::toSummary);
    }

    private void postEntry(JournalEntry entry) {
        entry.requireDraft();
        entry.post(entryNumbers.nextNumber(entry.getFiscalYear()), clock.instant());
        entries.saveAndFlush(entry);
        events.publishEvent(new LedgerChangedEvent(entry.getId()));
    }

    private JournalEntry requireDetailed(Long id) {
        return entries.findDetailedById(id).orElseThrow(() -> new NotFoundException("Asiento", id));
    }

    private List<LineSpec> resolve(List<JournalLineRequest> lines) {
        Map<String, Account> accountCache = new HashMap<>();
        Map<String, CostCenter> costCenterCache = new HashMap<>();
        List<LineSpec> specs = new ArrayList<>(lines.size());
        for (JournalLineRequest line : lines) {
            Account account = accountCache.computeIfAbsent(line.accountCode(), accountService::require);
            CostCenter costCenter = line.costCenterCode() == null || line.costCenterCode().isBlank()
                    ? null
                    : costCenterCache.computeIfAbsent(line.costCenterCode(), costCenterService::require);
            specs.add(new LineSpec(account, costCenter, Money.of(line.debit()), Money.of(line.credit()), line.description()));
        }
        return specs;
    }
}
