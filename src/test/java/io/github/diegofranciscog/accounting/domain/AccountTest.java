package io.github.diegofranciscog.accounting.domain;

import io.github.diegofranciscog.accounting.exception.DomainException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {

    private final Account assets = Account.create("1", "ACTIVO", null, null, false);

    @Test
    void childInheritsElementAndNextLevel() {
        Account current = Account.create("101", "ACTIVO CORRIENTE", assets, null, false);
        Account cash = Account.create("1010101", "CAJA", current, null, true);

        assertThat(cash.getElement()).isEqualTo(AccountElement.ASSET);
        assertThat(cash.getLevel()).isEqualTo(3);
        assertThat(cash.getNature()).isEqualTo(Nature.DEBIT);
        assertThat(cash.isContraAccount()).isFalse();
    }

    @Test
    void contraAccountKeepsExplicitNature() {
        Account depreciation = Account.create("1020112", "(-) DEPRECIACIÓN", assets, Nature.CREDIT, true);

        assertThat(depreciation.isContraAccount()).isTrue();
    }

    @Test
    void codeMustStartWithParentCode() {
        assertThatThrownBy(() -> Account.create("201", "PASIVO", assets, null, true))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("debe empezar con el código de su cuenta padre");
    }

    @Test
    void parentThatAcceptsEntriesCannotHaveChildren() {
        Account cash = Account.create("11", "CAJA", assets, null, true);

        assertThatThrownBy(() -> Account.create("111", "CAJA CHICA", cash, null, true))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("acepta movimientos");
    }

    @Test
    void rootMustHaveOneDigit() {
        assertThatThrownBy(() -> Account.create("10", "ACTIVO", null, null, false))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void codeMustStartWithKnownElement() {
        assertThatThrownBy(() -> Account.create("6", "ORDEN", null, null, false))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> AccountElement.fromCode("9"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("debe empezar con 1");
    }

    @Test
    void nameIsRequired() {
        assertThatThrownBy(() -> Account.create("2", " ", null, null, false))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void elementsKnowTheirNormalNature() {
        assertThat(AccountElement.INCOME.normalNature()).isEqualTo(Nature.CREDIT);
        assertThat(AccountElement.EXPENSE.normalNature()).isEqualTo(Nature.DEBIT);
        assertThat(AccountElement.EQUITY.isBalanceSheet()).isTrue();
        assertThat(AccountElement.INCOME.isBalanceSheet()).isFalse();
        assertThat(Nature.DEBIT.opposite()).isEqualTo(Nature.CREDIT);
    }
}
