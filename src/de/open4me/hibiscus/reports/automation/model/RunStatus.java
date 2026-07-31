package de.open4me.hibiscus.reports.automation.model;

public enum RunStatus
{
    GEPLANT("geplant"),
    LAEUFT("laeuft"),
    WARTET("wartet"),
    UEBERSPRUNGEN("uebersprungen"),
    ERFOLGREICH("erfolgreich"),
    FEHLGESCHLAGEN("fehlgeschlagen"),
    ABGEBROCHEN("abgebrochen"),
    TESTLAUF("testlauf");

    private final String value;

    RunStatus(String value)
    {
        this.value = value;
    }

    public String value()
    {
        return value;
    }

    public static RunStatus parse(String value)
    {
        for (RunStatus status : values())
        {
            if (status.value.equalsIgnoreCase(value))
                return status;
        }
        return GEPLANT;
    }
}
