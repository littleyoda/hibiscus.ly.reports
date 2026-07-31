package de.open4me.hibiscus.reports.automation.model;

public enum MissedTriggerPolicy
{
    IGNORIEREN("ignorieren"),
    NACHHOLEN("nachholen"),
    NACHFRAGEN("nachfragen");

    private final String value;

    MissedTriggerPolicy(String value)
    {
        this.value = value;
    }

    public String value()
    {
        return value;
    }

    public static MissedTriggerPolicy parse(String value)
    {
        for (MissedTriggerPolicy policy : values())
        {
            if (policy.value.equalsIgnoreCase(value))
                return policy;
        }
        return IGNORIEREN;
    }
}
