package io.github.diegofranciscog.accounting.domain;

import io.github.diegofranciscog.accounting.exception.DomainException;

/** Financial statement element, defined by the first digit of the account code (R4). */
public enum AccountElement {
    ASSET('1', Nature.DEBIT),
    LIABILITY('2', Nature.CREDIT),
    EQUITY('3', Nature.CREDIT),
    INCOME('4', Nature.CREDIT),
    EXPENSE('5', Nature.DEBIT);

    private final char digit;
    private final Nature normalNature;

    AccountElement(char digit, Nature normalNature) {
        this.digit = digit;
        this.normalNature = normalNature;
    }

    public static AccountElement fromCode(String code) {
        if (code == null || code.isEmpty()) {
            throw new DomainException("El código de cuenta es obligatorio");
        }
        for (AccountElement element : values()) {
            if (element.digit == code.charAt(0)) {
                return element;
            }
        }
        throw new DomainException("El código " + code + " debe empezar con 1 (activo), 2 (pasivo), 3 (patrimonio), 4 (ingresos) o 5 (costos y gastos)");
    }

    public Nature normalNature() {
        return normalNature;
    }

    public boolean isBalanceSheet() {
        return this == ASSET || this == LIABILITY || this == EQUITY;
    }
}
