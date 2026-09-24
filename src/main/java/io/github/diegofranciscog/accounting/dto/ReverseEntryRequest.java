package io.github.diegofranciscog.accounting.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** {@code date} defaults to today (America/Guayaquil) and must fall in an open period. */
public record ReverseEntryRequest(LocalDate date, @Size(max = 300) String reason) {
}
