package io.github.diegofranciscog.accounting.service;

import io.github.diegofranciscog.accounting.repository.BalanceViewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Keeps {@code mv_account_period_balances} in sync. It runs after COMMIT (only committed, balanced entries are
 * aggregated) and in the caller's thread, so a report requested right after posting already sees the entry.
 */
@Component
public class BalanceViewRefresher {

    private static final Logger log = LoggerFactory.getLogger(BalanceViewRefresher.class);

    private final BalanceViewRepository repository;

    public BalanceViewRefresher(BalanceViewRepository repository) {
        this.repository = repository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onLedgerChanged(LedgerChangedEvent event) {
        refreshSafely();
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onStartup() {
        refreshSafely();
    }

    private void refreshSafely() {
        try {
            repository.refresh();
        } catch (RuntimeException e) {
            // The entry is already committed; the next posting or restart rebuilds the view.
            log.error("No se pudo refrescar la vista de saldos: {}", e.getClass().getSimpleName());
        }
    }
}
