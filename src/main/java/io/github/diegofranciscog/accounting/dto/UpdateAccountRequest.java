package io.github.diegofranciscog.accounting.dto;

import jakarta.validation.constraints.Size;

/** Only the name and the active flag change; code, parent and element are immutable. */
public record UpdateAccountRequest(@Size(min = 1, max = 200) String name, Boolean active) {
}
