package de.open4me.hibiscus.reports.model;

import java.util.List;

public record FlowReport(List<Value> incomes, List<ExpenseGroup> expenses, int monthCount)
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
        return expenses.stream().mapToDouble(ExpenseGroup::amount).sum();
    }

    public double difference()
    {
        return incomeTotal() - expenseTotal();
    }

    public record Value(String key, String name, double amount, int color)
    {
    }

    public record ExpenseGroup(String key, String name, double amount, int color, List<Value> children)
    {
        public ExpenseGroup
        {
            children = List.copyOf(children);
        }
    }
}

