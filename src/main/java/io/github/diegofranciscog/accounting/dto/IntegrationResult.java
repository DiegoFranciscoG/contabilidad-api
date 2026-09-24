package io.github.diegofranciscog.accounting.dto;

/** {@code created} is false when the same event was already processed (idempotent retry). */
public record IntegrationResult(Long entryId, String entryNumber, boolean created) {
}
