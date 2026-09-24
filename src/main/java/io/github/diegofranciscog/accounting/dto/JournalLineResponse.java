package io.github.diegofranciscog.accounting.dto;

import java.math.BigDecimal;

public record JournalLineResponse(int lineNumber, String accountCode, String accountName, String costCenterCode,
                                  BigDecimal debit, BigDecimal credit, String description) {
}
