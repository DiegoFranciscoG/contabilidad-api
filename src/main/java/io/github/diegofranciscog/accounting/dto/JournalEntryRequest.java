package io.github.diegofranciscog.accounting.dto;

import io.github.diegofranciscog.accounting.domain.EntryType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Manual entry. {@code type} accepts STANDARD (default) or OPENING; closing and reversal entries have their own
 * operations. With {@code post = true} the entry is saved and posted in one step; omitted means draft.
 */
public record JournalEntryRequest(
        @NotNull LocalDate date,
        @NotBlank @Size(max = 500) String description,
        EntryType type,
        @NotEmpty @Size(min = 2, max = 500) List<@Valid @NotNull JournalLineRequest> lines,
        Boolean post) {

    public boolean shouldPost() {
        return Boolean.TRUE.equals(post);
    }
}
