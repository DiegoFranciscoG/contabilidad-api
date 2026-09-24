package io.github.diegofranciscog.accounting.domain;

import io.github.diegofranciscog.accounting.exception.DomainException;

import java.math.BigDecimal;
import java.util.List;

/** Double-entry rules shared by every journal entry (R1, R2). The database repeats them as a safety net. */
public final class EntryRules {

    public static final int MAX_LINES = 500;

    private EntryRules() {
    }

    /**
     * Validates the lines of an entry and returns its total (sum of debits = sum of credits).
     *
     * @throws DomainException when the entry is not a valid double-entry record
     */
    public static BigDecimal requireBalanced(List<? extends LineAmounts> lines) {
        if (lines == null || lines.size() < 2) {
            throw new DomainException("Un asiento necesita al menos dos líneas");
        }
        if (lines.size() > MAX_LINES) {
            throw new DomainException("Un asiento admite máximo " + MAX_LINES + " líneas");
        }
        BigDecimal totalDebit = Money.ZERO;
        BigDecimal totalCredit = Money.ZERO;
        for (int i = 0; i < lines.size(); i++) {
            LineAmounts line = lines.get(i);
            BigDecimal debit = Money.of(line.debit());
            BigDecimal credit = Money.of(line.credit());
            if (debit.signum() < 0 || credit.signum() < 0) {
                throw new DomainException("Línea " + (i + 1) + ": los importes no pueden ser negativos");
            }
            if ((debit.signum() > 0) == (credit.signum() > 0)) {
                throw new DomainException("Línea " + (i + 1) + ": debe tener valor en el debe o en el haber, no en ambos ni en ninguno");
            }
            totalDebit = totalDebit.add(debit);
            totalCredit = totalCredit.add(credit);
        }
        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new DomainException("El asiento no cuadra: debe " + totalDebit.toPlainString() + " ≠ haber " + totalCredit.toPlainString());
        }
        return totalDebit;
    }

    /** Signed balance of an account seen from its nature: positive means a normal balance. */
    public static BigDecimal balance(Nature nature, BigDecimal debit, BigDecimal credit) {
        return nature == Nature.DEBIT ? debit.subtract(credit) : credit.subtract(debit);
    }
}
