package de.open4me.hibiscus.reports.ui;

import java.time.LocalDate;
import java.util.Set;

import de.open4me.hibiscus.reports.model.SankeyGraph;

record FlowTransactionSelection(String title, LocalDate from, LocalDate to, Set<String> accountIds,
                                SankeyGraph.TransactionFilter filter, Sign sign,
                                boolean excludeSkippedCategories)
{
    FlowTransactionSelection(String title, LocalDate from, LocalDate to, Set<String> accountIds,
                             SankeyGraph.TransactionFilter filter)
    {
        this(title, from, to, accountIds, filter, Sign.ALL, false);
    }

    FlowTransactionSelection
    {
        accountIds = accountIds == null ? Set.of() : Set.copyOf(accountIds);
        sign = sign == null ? Sign.ALL : sign;
    }

    enum Sign
    {
        ALL(0),
        INCOME(1),
        EXPENSE(-1);

        private final int value;

        Sign(int value)
        {
            this.value = value;
        }

        boolean matches(double amount)
        {
            return value == 0 || Math.signum(amount) == value;
        }
    }
}
