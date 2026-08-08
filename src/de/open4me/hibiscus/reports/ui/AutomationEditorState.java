package de.open4me.hibiscus.reports.ui;

import de.open4me.hibiscus.reports.automation.model.AutomationTriggerTypes;

record AutomationEditorState(String name, String description, String missedPolicy, boolean scheduleActive,
                             String triggerType, String scheduleExpression, String script)
{
    AutomationEditorState(String name, String description, String missedPolicy, boolean scheduleActive,
                          String scheduleExpression, String script)
    {
        this(name, description, missedPolicy, scheduleActive, AutomationTriggerTypes.CRON, scheduleExpression, script);
    }

    AutomationEditorState
    {
        name = text(name);
        description = text(description);
        missedPolicy = text(missedPolicy);
        triggerType = text(triggerType);
        scheduleExpression = text(scheduleExpression).trim();
        script = text(script);
    }

    static AutomationEditorState empty()
    {
        return new AutomationEditorState("", "", "", false, "", "", "");
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
