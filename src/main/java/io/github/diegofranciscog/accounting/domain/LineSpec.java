package io.github.diegofranciscog.accounting.domain;

import java.math.BigDecimal;

/** Resolved data for one journal line before it becomes an entity. */
public record LineSpec(Account account, CostCenter costCenter, BigDecimal debit, BigDecimal credit, String description)
        implements LineAmounts {

    public static LineSpec debit(Account account, BigDecimal amount, CostCenter costCenter, String description) {
        return new LineSpec(account, costCenter, amount, Money.ZERO, description);
    }

    public static LineSpec credit(Account account, BigDecimal amount, CostCenter costCenter, String description) {
        return new LineSpec(account, costCenter, Money.ZERO, amount, description);
    }

    /** Same account and amount on the opposite side, used by reversal entries. */
    public LineSpec mirrored() {
        return new LineSpec(account, costCenter, credit, debit, description);
    }
}
