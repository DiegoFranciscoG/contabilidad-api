package io.github.diegofranciscog.accounting.dto.report;

import java.math.BigDecimal;

/** One printable line of a financial statement. */
public record StatementLine(Kind kind, String code, String label, int indent, BigDecimal amount) {

    public enum Kind {
        HEADER,
        DETAIL,
        SUBTOTAL,
        TOTAL
    }
}
