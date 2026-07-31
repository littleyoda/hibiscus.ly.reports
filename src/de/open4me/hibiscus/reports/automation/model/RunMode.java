package de.open4me.hibiscus.reports.automation.model;

public enum RunMode
{
    SINGLE("single"),
    QUEUED("queued"),
    PARALLEL("parallel");

    private final String value;

    RunMode(String value)
    {
        this.value = value;
    }

    public String value()
    {
        return value;
    }

    public static RunMode parse(String value)
    {
        for (RunMode mode : values())
        {
            if (mode.value.equalsIgnoreCase(value))
                return mode;
        }
        return SINGLE;
    }
}
