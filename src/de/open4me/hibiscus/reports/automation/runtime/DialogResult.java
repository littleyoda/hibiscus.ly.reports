package de.open4me.hibiscus.reports.automation.runtime;

public final class DialogResult<T>
{
    private final boolean abgebrochen;
    private final T wert;

    private DialogResult(boolean abgebrochen, T wert)
    {
        this.abgebrochen = abgebrochen;
        this.wert = wert;
    }

    public static <T> DialogResult<T> ok(T wert)
    {
        return new DialogResult<>(false, wert);
    }

    public static <T> DialogResult<T> abgebrochen()
    {
        return new DialogResult<>(true, null);
    }

    public boolean getAbgebrochen()
    {
        return abgebrochen;
    }

    public T getWert()
    {
        return wert;
    }
}
