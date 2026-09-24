package io.github.diegofranciscog.accounting.domain;

import io.github.diegofranciscog.accounting.exception.DomainException;
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
import jakarta.persistence.Table;

/** Which account receives each amount of an automatic entry (for example VAT_PAYABLE → 201070101). */
@Entity
@Table(name = "event_account_mappings")
public class EventAccountMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30, updatable = false)
    private EventType eventType;

    @Column(name = "account_role", nullable = false, length = 40, updatable = false)
    private String accountRole;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    protected EventAccountMapping() {
    }

    public void changeAccount(Account newAccount) {
        if (!newAccount.isAcceptsEntries() || !newAccount.isActive()) {
            throw new DomainException("La cuenta " + newAccount.getCode() + " debe ser de movimiento y estar activa");
        }
        this.account = newAccount;
    }

    public Long getId() {
        return id;
    }

    public EventType getEventType() {
        return eventType;
    }

    public String getAccountRole() {
        return accountRole;
    }

    public Account getAccount() {
        return account;
    }
}
