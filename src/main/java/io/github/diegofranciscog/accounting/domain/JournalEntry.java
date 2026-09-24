package io.github.diegofranciscog.accounting.domain;

import io.github.diegofranciscog.accounting.exception.DomainException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Journal entry (asiento). A draft can be edited; once posted it is immutable and can only be
 * cancelled with a reversal entry (R6). Every saved entry, draft or posted, is balanced (R1).
 */
@Entity
@Table(name = "journal_entries")
public class JournalEntry {

    private static final int MAX_DESCRIPTION = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entry_number")
    private Integer entryNumber;

    @Column(name = "fiscal_year", nullable = false)
    private short fiscalYear;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "period_id", nullable = false)
    private FiscalPeriod period;

    @Column(nullable = false, length = MAX_DESCRIPTION)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private EntryStatus status = EntryStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 10, updatable = false)
    private EntryType entryType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private EntrySource source;

    @Column(name = "source_reference", length = 100, updatable = false)
    private String sourceReference;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reverses_entry_id", updatable = false)
    private JournalEntry reversesEntry;

    @Column(name = "total_debit", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalDebit;

    @Column(name = "total_credit", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalCredit;

    @Column(name = "created_by", nullable = false, length = 254, updatable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Version
    private long version;

    @OneToMany(mappedBy = "entry", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNumber")
    private List<JournalLine> lines = new ArrayList<>();

    protected JournalEntry() {
    }

    private JournalEntry(EntryType entryType, EntrySource source, String sourceReference, JournalEntry reversesEntry,
                         String createdBy, Instant now) {
        this.entryType = entryType;
        this.source = source;
        this.sourceReference = sourceReference;
        this.reversesEntry = reversesEntry;
        this.createdBy = createdBy;
        this.createdAt = now;
    }

    /** Creates a balanced draft entry. */
    public static JournalEntry draft(EntryHeader header, List<LineSpec> lines, String createdBy, Instant now) {
        if (header.type() == EntryType.REVERSAL) {
            throw new DomainException("Los asientos de reverso se generan con la operación de reverso");
        }
        JournalEntry entry = new JournalEntry(header.type(), header.source(), header.sourceReference(), null, createdBy, now);
        entry.fill(header.period(), header.date(), header.description(), lines);
        return entry;
    }

    /** Builds the mirror entry that cancels this posted entry (R6). */
    public JournalEntry reversal(FiscalPeriod targetPeriod, LocalDate date, String reason, String createdBy, Instant now) {
        if (status != EntryStatus.POSTED) {
            throw new DomainException("Solo se reversan asientos contabilizados; un borrador se edita o elimina");
        }
        if (entryType == EntryType.REVERSAL) {
            throw new DomainException("Un asiento de reverso no se reversa; registra un asiento nuevo");
        }
        String text = "Reverso del asiento " + fiscalYear + "-" + entryNumber
                + (reason == null || reason.isBlank() ? "" : ": " + reason.strip());
        JournalEntry reversal = new JournalEntry(EntryType.REVERSAL, EntrySource.MANUAL, null, this, createdBy, now);
        reversal.fill(targetPeriod, date, truncate(text), lines.stream().map(line -> line.toSpec().mirrored()).toList());
        return reversal;
    }

    /** Replaces header data and lines of a draft. */
    public void update(FiscalPeriod newPeriod, LocalDate date, String newDescription, List<LineSpec> newLines) {
        requireDraft();
        period.requireOpen();
        fill(newPeriod, date, newDescription, newLines);
    }

    /** Posts the draft with the next number of its fiscal year. */
    public void post(int number, Instant now) {
        requireDraft();
        period.requireOpen();
        this.entryNumber = number;
        this.status = EntryStatus.POSTED;
        this.postedAt = now;
    }

    public void requireDraft() {
        if (status != EntryStatus.DRAFT) {
            throw new DomainException("El asiento " + fiscalYear + "-" + entryNumber
                    + " está contabilizado; corrígelo con un asiento de reverso");
        }
    }

    private void fill(FiscalPeriod newPeriod, LocalDate date, String newDescription, List<LineSpec> newLines) {
        if (newPeriod == null || date == null) {
            throw new DomainException("La fecha del asiento es obligatoria");
        }
        newPeriod.requireOpen();
        if (!newPeriod.contains(date)) {
            throw new DomainException("La fecha " + date + " no pertenece al periodo " + newPeriod.label());
        }
        if (newDescription == null || newDescription.isBlank() || newDescription.length() > MAX_DESCRIPTION) {
            throw new DomainException("La glosa es obligatoria (máximo " + MAX_DESCRIPTION + " caracteres)");
        }
        BigDecimal total = EntryRules.requireBalanced(newLines);
        for (LineSpec line : newLines) {
            Account account = line.account();
            if (!account.isAcceptsEntries()) {
                throw new DomainException("La cuenta " + account.getCode() + " es de grupo y no acepta movimientos");
            }
            if (!account.isActive()) {
                throw new DomainException("La cuenta " + account.getCode() + " está inactiva");
            }
            if (line.costCenter() != null && !line.costCenter().isActive()) {
                throw new DomainException("El centro de costo " + line.costCenter().getCode() + " está inactivo");
            }
        }
        this.period = newPeriod;
        this.entryDate = date;
        this.fiscalYear = (short) date.getYear();
        this.description = newDescription.strip();
        this.totalDebit = total;
        this.totalCredit = total;
        this.lines.clear();
        for (int i = 0; i < newLines.size(); i++) {
            this.lines.add(new JournalLine(this, i + 1, newLines.get(i)));
        }
    }

    private static String truncate(String text) {
        return text.length() <= MAX_DESCRIPTION ? text : text.substring(0, MAX_DESCRIPTION);
    }

    public boolean isPosted() {
        return status == EntryStatus.POSTED;
    }

    public Long getId() {
        return id;
    }

    public Integer getEntryNumber() {
        return entryNumber;
    }

    public int getFiscalYear() {
        return fiscalYear;
    }

    public LocalDate getEntryDate() {
        return entryDate;
    }

    public FiscalPeriod getPeriod() {
        return period;
    }

    public String getDescription() {
        return description;
    }

    public EntryStatus getStatus() {
        return status;
    }

    public EntryType getEntryType() {
        return entryType;
    }

    public EntrySource getSource() {
        return source;
    }

    public String getSourceReference() {
        return sourceReference;
    }

    public JournalEntry getReversesEntry() {
        return reversesEntry;
    }

    public BigDecimal getTotalDebit() {
        return totalDebit;
    }

    public BigDecimal getTotalCredit() {
        return totalCredit;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPostedAt() {
        return postedAt;
    }

    public List<JournalLine> getLines() {
        return Collections.unmodifiableList(lines);
    }
}
