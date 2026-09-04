package de.open4me.hibiscus.reports.ui;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import de.open4me.hibiscus.reports.model.SankeyGraph;

final class FlowVisibilityState
{
    private final Set<String> excluded = new LinkedHashSet<>();
    private final Deque<List<String>> history = new ArrayDeque<>();

    Set<String> excludedKeys()
    {
        return Set.copyOf(excluded);
    }

    boolean hasExclusions()
    {
        return !excluded.isEmpty();
    }

    boolean hide(SankeyGraph.Node node)
    {
        if (node == null || node.exclusionKeys().isEmpty())
            return false;
        List<String> added = new ArrayList<>();
        for (String key : node.exclusionKeys())
        {
            if (excluded.add(key))
                added.add(key);
        }
        if (added.isEmpty())
            return false;
        history.push(List.copyOf(added));
        return true;
    }

    boolean undo()
    {
        if (history.isEmpty())
            return false;
        excluded.removeAll(history.pop());
        return true;
    }

    boolean showAll()
    {
        if (excluded.isEmpty())
            return false;
        excluded.clear();
        history.clear();
        return true;
    }
}
