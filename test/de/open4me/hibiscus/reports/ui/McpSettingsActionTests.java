package de.open4me.hibiscus.reports.ui;

public final class McpSettingsActionTests
{
    private McpSettingsActionTests()
    {
    }

    public static void run()
    {
        defersBindingChangeWhileRunning();
        restartsWhenBindingDoesNotChange();
        restartsWhenServerIsStopped();
        restartsWhenDisablingServer();
    }

    private static void defersBindingChangeWhileRunning()
    {
        check(!McpSettingsAction.restartRequired(true, true, false, true, true),
            "running bind address change deferred");
    }

    private static void restartsWhenBindingDoesNotChange()
    {
        check(McpSettingsAction.restartRequired(true, true, false, true, false),
            "unchanged binding restarts");
    }

    private static void restartsWhenServerIsStopped()
    {
        check(McpSettingsAction.restartRequired(false, true, false, true, true),
            "stopped server can start with new binding");
    }

    private static void restartsWhenDisablingServer()
    {
        check(McpSettingsAction.restartRequired(true, true, false, false, true),
            "disable stops server");
    }

    private static void check(boolean condition, String message)
    {
        if (!condition)
            throw new AssertionError(message);
    }
}
