package io.github.diegofranciscog.accounting.domain;

import io.github.diegofranciscog.accounting.exception.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;

/** Monthly accounting period. Once closed it never reopens (R6). */
@Entity
@Table(name = "fiscal_periods")
public class FiscalPeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private short year;

    @Column(nullable = false, updatable = false)
    private short month;

    @Column(name = "start_date", nullable = false, updatable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false, updatable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6)
    private PeriodStatus status = PeriodStatus.OPEN;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_by", length = 254)
    private String closedBy;

    protected FiscalPeriod() {
    }

    public FiscalPeriod(YearMonth yearMonth) {
        this.year = (short) yearMonth.getYear();
        this.month = (short) yearMonth.getMonthValue();
        this.startDate = yearMonth.atDay(1);
        this.endDate = yearMonth.atEndOfMonth();
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    public boolean isOpen() {
        return status == PeriodStatus.OPEN;
    }

    public void requireOpen() {
        if (!isOpen()) {
            throw new DomainException("El periodo " + label() + " está cerrado; corrige con un asiento de reverso en un periodo abierto");
        }
    }

    public void close(String user, Instant now) {
        requireOpen();
        this.status = PeriodStatus.CLOSED;
        this.closedAt = now;
        this.closedBy = user;
    }

    public YearMonth yearMonth() {
        return YearMonth.of(year, month);
    }

    public String label() {
        return yearMonth().toString();
    }

    public Long getId() {
        return id;
    }

    public int getYear() {
        return year;
    }

    public int getMonth() {
        return month;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public PeriodStatus getStatus() {
        return status;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public String getClosedBy() {
        return closedBy;
    }
}
