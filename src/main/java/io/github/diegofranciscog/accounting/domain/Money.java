package io.github.diegofranciscog.accounting.domain;

import io.github.diegofranciscog.accounting.exception.DomainException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Amounts are US dollars with two decimals (R11, LORTI art. 20). */
public final class Money {

    public static final int SCALE = 2;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE);

    private Money() {
    }

    /** Normalises an amount to two decimals, rejecting values that would lose precision. */
    public static BigDecimal of(BigDecimal amount) {
        if (amount == null) {
            return ZERO;
        }
        if (amount.stripTrailingZeros().scale() > SCALE) {
            throw new DomainException("Los importes admiten máximo dos decimales: " + amount.toPlainString());
        }
        return amount.setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    public static BigDecimal of(String amount) {
        return of(new BigDecimal(amount));
    }

    public static boolean isZero(BigDecimal amount) {
        return amount.signum() == 0;
    }
}
