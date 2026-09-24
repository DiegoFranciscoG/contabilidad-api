package io.github.diegofranciscog.accounting.service.report;

import io.github.diegofranciscog.accounting.domain.Account;
import io.github.diegofranciscog.accounting.domain.AccountElement;
import io.github.diegofranciscog.accounting.domain.Nature;

/** Immutable view of an account used by the report builders, so they run without JPA or a database. */
public record AccountNode(long id, String code, String name, Long parentId, int level, AccountElement element,
                          Nature nature, boolean detail) {

    public static AccountNode of(Account account) {
        return new AccountNode(account.getId(), account.getCode(), account.getName(),
                account.getParent() == null ? null : account.getParent().getId(), account.getLevel(),
                account.getElement(), account.getNature(), account.isAcceptsEntries());
    }

    public boolean isUnder(String prefix) {
        return code.startsWith(prefix);
    }
}
