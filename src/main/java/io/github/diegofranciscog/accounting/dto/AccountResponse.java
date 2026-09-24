package io.github.diegofranciscog.accounting.dto;

import io.github.diegofranciscog.accounting.domain.AccountElement;
import io.github.diegofranciscog.accounting.domain.Nature;

public record AccountResponse(Long id, String code, String name, String parentCode, int level, AccountElement element,
                              Nature nature, boolean acceptsEntries, boolean active) {
}
