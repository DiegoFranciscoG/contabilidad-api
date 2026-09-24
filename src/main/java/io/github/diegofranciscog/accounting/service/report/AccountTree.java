package io.github.diegofranciscog.accounting.service.report;

import io.github.diegofranciscog.accounting.domain.AccountMovement;
import io.github.diegofranciscog.accounting.domain.Money;
import io.github.diegofranciscog.accounting.domain.Nature;
import io.github.diegofranciscog.accounting.dto.report.StatementLine;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Chart of accounts with movements rolled up from detail accounts to every ancestor.
 * Statement amounts use the element's normal side, so contra accounts (for example accumulated depreciation)
 * appear as negative amounts and reduce their group.
 */
final class AccountTree {

    private final List<AccountNode> nodes;
    private final Map<Long, AccountMovement> rolledUp = new HashMap<>();

    AccountTree(List<AccountNode> accounts, Map<Long, AccountMovement> movements) {
        this.nodes = accounts.stream().sorted(Comparator.comparing(AccountNode::code)).toList();
        Map<Long, AccountNode> byId = new HashMap<>();
        accounts.forEach(node -> byId.put(node.id(), node));
        for (AccountNode node : accounts) {
            AccountMovement movement = movements.get(node.id());
            if (!node.detail() || movement == null || movement.isZero()) {
                continue;
            }
            for (AccountNode current = node; current != null;
                 current = current.parentId() == null ? null : byId.get(current.parentId())) {
                long id = current.id();
                rolledUp.merge(id, new AccountMovement(id, movement.debit(), movement.credit()), AccountMovement::plus);
            }
        }
    }

    List<AccountNode> nodes() {
        return nodes;
    }

    AccountMovement movement(AccountNode node) {
        return rolledUp.getOrDefault(node.id(), AccountMovement.empty(node.id()));
    }

    /** Amount seen from the element's normal side (assets and expenses: debit − credit). */
    BigDecimal amount(AccountNode node) {
        AccountMovement movement = movement(node);
        return node.element().normalNature() == Nature.DEBIT ? movement.net() : movement.net().negate();
    }

    /** Sum over detail accounts only, so nothing is counted twice. */
    BigDecimal sumOfDetails(Predicate<AccountNode> filter) {
        BigDecimal total = Money.ZERO;
        for (AccountNode node : nodes) {
            if (node.detail() && filter.test(node)) {
                total = total.add(amount(node));
            }
        }
        return total;
    }

    /** Non-zero accounts under {@code prefix} down to {@code maxLevel}, indented relative to the prefix. */
    List<StatementLine> detailLines(String prefix, int prefixLevel, int maxLevel) {
        List<StatementLine> lines = new ArrayList<>();
        for (AccountNode node : nodes) {
            if (node.isUnder(prefix) && !node.code().equals(prefix) && node.level() <= maxLevel) {
                BigDecimal amount = amount(node);
                if (amount.signum() != 0) {
                    lines.add(new StatementLine(StatementLine.Kind.DETAIL, node.code(), node.name(),
                            Math.max(1, node.level() - prefixLevel), amount));
                }
            }
        }
        return lines;
    }

    /** Non-zero detail accounts matching the filter, used for "other" buckets without a common prefix. */
    List<StatementLine> detailAccountLines(Predicate<AccountNode> filter) {
        List<StatementLine> lines = new ArrayList<>();
        for (AccountNode node : nodes) {
            if (node.detail() && filter.test(node)) {
                BigDecimal amount = amount(node);
                if (amount.signum() != 0) {
                    lines.add(new StatementLine(StatementLine.Kind.DETAIL, node.code(), node.name(), 1, amount));
                }
            }
        }
        return lines;
    }
}
