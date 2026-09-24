package io.github.diegofranciscog.accounting.dto;

import io.github.diegofranciscog.accounting.domain.EventType;

public record EventAccountMappingResponse(Long id, EventType eventType, String accountRole, String accountCode,
                                          String accountName) {
}
