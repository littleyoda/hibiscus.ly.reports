package de.open4me.hibiscus.reports.ui;

record AutomationEditorState(String name, String description, String missedPolicy, boolean scheduleActive,
                             String scheduleExpression, String script)
{
    AutomationEditorState
    {
        name = text(name);
        description = text(description);
        missedPolicy = text(missedPolicy);
        scheduleExpression = text(scheduleExpression).trim();
        script = text(script);
    }

    static AutomationEditorState empty()
    {
        return new AutomationEditorState("", "", "", false, "", "");
    }

    boolean differsFrom(AutomationEditorState other)
    {
        return !equals(other == null ? empty() : other);
    }

    private static String text(String value)
    {
        return value == null ? "" : value;
    }
}
