package io.github.diegofranciscog.accounting.dto;

import java.util.List;

public record AccountImportResult(int created, int skipped, List<String> skippedCodes) {
}
