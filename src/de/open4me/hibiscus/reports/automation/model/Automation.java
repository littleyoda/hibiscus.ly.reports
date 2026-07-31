package de.open4me.hibiscus.reports.automation.model;

public record Automation(String id, String name, String description, boolean active, RunMode mode,
                         MissedTriggerPolicy missedTriggerPolicy, String script, int historyLimit)
{
    public Automation
    {
        name = text(name);
        description = text(description);
        mode = mode == null ? RunMode.SINGLE : mode;
        missedTriggerPolicy = missedTriggerPolicy == null ? MissedTriggerPolicy.IGNORIEREN : missedTriggerPolicy;
        script = text(script);
        historyLimit = historyLimit <= 0 ? 100 : historyLimit;
    }

    public Automation withId(String id)
    {
        return new Automation(id, name, description, active, mode, missedTriggerPolicy, script, historyLimit);
    }

    private static String text(String value)
    {
        return value == null ? "" : value;
    }
}
