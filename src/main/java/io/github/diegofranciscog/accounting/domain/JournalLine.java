package io.github.diegofranciscog.accounting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "journal_lines")
public class JournalLine implements LineAmounts {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entry_id", nullable = false, updatable = false)
    private JournalEntry entry;

    @Column(name = "line_number", nullable = false)
    private short lineNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cost_center_id")
    private CostCenter costCenter;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal debit;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal credit;

    @Column(length = 250)
    private String description;

    protected JournalLine() {
    }

    JournalLine(JournalEntry entry, int lineNumber, LineSpec spec) {
        this.entry = entry;
        this.lineNumber = (short) lineNumber;
        this.account = spec.account();
        this.costCenter = spec.costCenter();
        this.debit = Money.of(spec.debit());
        this.credit = Money.of(spec.credit());
        this.description = spec.description() == null || spec.description().isBlank() ? null : spec.description().strip();
    }

    public LineSpec toSpec() {
        return new LineSpec(account, costCenter, debit, credit, description);
    }

    public Long getId() {
        return id;
    }

    public JournalEntry getEntry() {
        return entry;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public Account getAccount() {
        return account;
    }

    public CostCenter getCostCenter() {
        return costCenter;
    }

    @Override
    public BigDecimal debit() {
        return debit;
    }

    @Override
    public BigDecimal credit() {
        return credit;
    }

    public String getDescription() {
        return description;
    }
}
