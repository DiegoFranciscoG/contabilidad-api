package io.github.diegofranciscog.accounting.domain;

/** Side on which an account normally carries its balance (R5). */
public enum Nature {
    DEBIT,
    CREDIT;

    public Nature opposite() {
        return this == DEBIT ? CREDIT : DEBIT;
    }
}
