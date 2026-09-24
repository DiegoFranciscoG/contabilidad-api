package io.github.diegofranciscog.accounting.dto;

import jakarta.validation.constraints.Size;

public record UpdateCostCenterRequest(@Size(min = 1, max = 120) String name, Boolean active) {
}
