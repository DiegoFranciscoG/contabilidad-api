package io.github.diegofranciscog.accounting.domain;

import io.github.diegofranciscog.accounting.exception.DomainException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static io.github.diegofranciscog.accounting.domain.TestChart.CASH;
import static io.github.diegofranciscog.accounting.domain.TestChart.SALES;
import static io.github.diegofranciscog.accounting.domain.TestChart.credit;
import static io.github.diegofranciscog.accounting.domain.TestChart.debit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EntryRulesTest {

    @Test
    void balancedEntryReturnsItsTotal() {
        BigDecimal total = EntryRules.requireBalanced(List.of(debit(CASH, "115.00"), credit(SALES, "100"), credit(SALES, "15")));

        assertThat(total).isEqualByComparingTo("115.00");
    }

    @Test
    void rejectsUnbalancedEntry() {
        assertThatThrownBy(() -> EntryRules.requireBalanced(List.of(debit(CASH, "100.00"), credit(SALES, "99.99"))))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("no cuadra");
    }

    @Test
    void rejectsSingleLine() {
        assertThatThrownBy(() -> EntryRules.requireBalanced(List.of(debit(CASH, "1"))))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("al menos dos líneas");
    }

    @Test
    void rejectsLineWithBothSides() {
        LineSpec both = new LineSpec(CASH, null, new BigDecimal("5"), new BigDecimal("5"), null);

        assertThatThrownBy(() -> EntryRules.requireBalanced(List.of(both, both)))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("no en ambos");
    }

    @Test
    void rejectsZeroLine() {
        assertThatThrownBy(() -> EntryRules.requireBalanced(List.of(debit(CASH, "0"), credit(SALES, "0"))))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void rejectsNegativeAmounts() {
        assertThatThrownBy(() -> EntryRules.requireBalanced(List.of(debit(CASH, "-5"), credit(SALES, "-5"))))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("negativos");
    }

    @Test
    void rejectsMoreThanTwoDecimals() {
        assertThatThrownBy(() -> EntryRules.requireBalanced(List.of(debit(CASH, "1.005"), credit(SALES, "1.005"))))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("dos decimales");
    }

    @Test
    void balanceFollowsNature() {
        assertThat(EntryRules.balance(Nature.DEBIT, new BigDecimal("10"), new BigDecimal("3"))).isEqualByComparingTo("7");
        assertThat(EntryRules.balance(Nature.CREDIT, new BigDecimal("10"), new BigDecimal("3"))).isEqualByComparingTo("-7");
    }
}
