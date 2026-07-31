package de.open4me.hibiscus.reports.automation.runtime;

public final class AutomationContext
{
    private final String automationId;
    private final String automationName;
    private final String runId;
    private final String triggerId;
    private final String source;
    private final boolean testlauf;
    private final boolean writeAllowed;

    public AutomationContext(String automationId, String automationName, String runId, String triggerId,
                             String source, boolean testlauf, boolean writeAllowed)
    {
        this.automationId = automationId;
        this.automationName = automationName;
        this.runId = runId;
        this.triggerId = triggerId;
        this.source = source;
        this.testlauf = testlauf;
        this.writeAllowed = writeAllowed;
    }

    public String getAutomationId()
    {
        return automationId;
    }

    public String getAutomationName()
    {
        return automationName;
    }

    public String getRunId()
    {
        return runId;
    }

    public String getTriggerId()
    {
        return triggerId;
    }

    public String getSource()
    {
        return source;
    }

    public boolean getTestlauf()
    {
        return testlauf;
    }

    public boolean getWriteAllowed()
    {
        return writeAllowed;
    }
}
