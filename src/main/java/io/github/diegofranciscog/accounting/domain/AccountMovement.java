package io.github.diegofranciscog.accounting.domain;

import java.math.BigDecimal;

/** Debit and credit sums of one account over a range of periods. */
public record AccountMovement(long accountId, BigDecimal debit, BigDecimal credit) {

    public static AccountMovement empty(long accountId) {
        return new AccountMovement(accountId, Money.ZERO, Money.ZERO);
    }

    public AccountMovement plus(AccountMovement other) {
        return new AccountMovement(accountId, debit.add(other.debit), credit.add(other.credit));
    }

    /** Debit minus credit. */
    public BigDecimal net() {
        return debit.subtract(credit);
    }

    public boolean isZero() {
        return debit.signum() == 0 && credit.signum() == 0;
    }
}
