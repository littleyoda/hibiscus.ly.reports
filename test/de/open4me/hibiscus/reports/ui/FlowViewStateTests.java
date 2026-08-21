package de.open4me.hibiscus.reports.ui;

import java.util.HashSet;
import java.util.Set;

public final class FlowViewStateTests
{
    private FlowViewStateTests()
    {
    }

    public static void run()
    {
        Set<String> expanded = new HashSet<>(Set.of("housing", "insurance"));
        FlowViewState state = new FlowViewState(expanded);
        expanded.clear();

        check(state.expandedCategories().equals(Set.of("housing", "insurance")),
            "navigation state must retain the expanded categories");
        check(new FlowViewState(null).expandedCategories().isEmpty(),
            "missing navigation state must restore no expanded categories");
    }

    private static void check(boolean condition, String message)
    {
        if (!condition)
            throw new AssertionError(message);
    }
}
