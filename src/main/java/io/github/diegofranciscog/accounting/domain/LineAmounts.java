package io.github.diegofranciscog.accounting.domain;

import java.math.BigDecimal;

/** Debit/credit pair of a journal line, shared by entities, requests and property tests. */
public interface LineAmounts {

    BigDecimal debit();

    BigDecimal credit();
}
