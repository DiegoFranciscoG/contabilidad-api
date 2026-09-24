package io.github.diegofranciscog.accounting.service;

/** Published when posted entries change the balances (posting, reversal, closing or automatic entry). */
public record LedgerChangedEvent(Long entryId) {
}
