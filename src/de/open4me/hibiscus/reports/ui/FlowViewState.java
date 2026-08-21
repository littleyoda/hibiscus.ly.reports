package de.open4me.hibiscus.reports.ui;

import java.util.Set;

record FlowViewState(Set<String> expandedCategories)
{
    FlowViewState
    {
        expandedCategories = expandedCategories == null ? Set.of() : Set.copyOf(expandedCategories);
    }
}
