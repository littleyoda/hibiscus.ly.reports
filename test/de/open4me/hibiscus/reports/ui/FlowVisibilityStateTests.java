package de.open4me.hibiscus.reports.ui;

import java.util.List;
import java.util.Set;

import de.open4me.hibiscus.reports.model.SankeyGraph;

public final class FlowVisibilityStateTests
{
    private FlowVisibilityStateTests()
    {
    }

    public static void run()
    {
        FlowVisibilityState state = new FlowVisibilityState();
        state.hide(node("Sonstige", "expense:books", "expense:holiday"));
        state.hide(node("Versicherung", "expense:insurance"));

        check(state.excludedKeys().equals(Set.of("expense:books", "expense:holiday", "expense:insurance")),
            "all hidden keys");
        check(state.undo(), "undo available");
        check(state.excludedKeys().equals(Set.of("expense:books", "expense:holiday")),
            "undo restores the last area");
        check(state.showAll(), "show all available");
        check(!state.hasExclusions(), "show all clears exclusions");
        check(!state.undo(), "show all clears history");
        check(!state.hide(node("Überschuss")), "summary nodes cannot be hidden");
    }

    private static SankeyGraph.Node node(String name, String... exclusionKeys)
    {
        return new SankeyGraph.Node(name, name, 10d, 100d, 0x123456, 2, null, null,
            List.of(exclusionKeys));
    }

    private static void check(boolean condition, String message)
    {
        if (!condition)
            throw new AssertionError(message);
    }
}
