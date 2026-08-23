package de.open4me.hibiscus.reports.model;

import java.util.List;

public record FlowReport(List<Value> incomes, List<ExpenseNode> expenses, int monthCount)
{
    public FlowReport
    {
        incomes = List.copyOf(incomes);
        expenses = List.copyOf(expenses);
        monthCount = Math.max(1, monthCount);
    }

    public double incomeTotal()
    {
        return incomes.stream().mapToDouble(Value::amount).sum();
    }

    public double expenseTotal()
    {
        return expenses.stream().mapToDouble(ExpenseNode::amount).sum();
    }

    public double difference()
    {
        return incomeTotal() - expenseTotal();
    }

    public record Value(String key, String name, double amount, int color, boolean includeChildren)
    {
        public Value(String key, String name, double amount, int color)
        {
            this(key, name, amount, color, false);
        }
    }

    public record ExpenseNode(String key, String categoryId, String name, double amount, int color,
                              boolean includeChildren, List<ExpenseNode> children)
    {
        public ExpenseNode
        {
            children = List.copyOf(children);
        }
    }
}
