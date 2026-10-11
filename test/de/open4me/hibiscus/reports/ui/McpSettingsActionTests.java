package de.open4me.hibiscus.reports.ui;

public final class McpSettingsActionTests
{
    private McpSettingsActionTests()
    {
    }

    public static void run()
    {
        defersBindingChangeWhileRunning();
        keepsRunningServerForPasswordOnlyChange();
        restartsWhenPortChanges();
        restartsWhenServerIsStopped();
        restartsWhenDisablingServer();
    }

    private static void defersBindingChangeWhileRunning()
    {
        check(!McpSettingsAction.restartRequired(true, true, false, 37653, true, true, 37653),
            "running bind address change deferred");
    }

    private static void keepsRunningServerForPasswordOnlyChange()
    {
        check(!McpSettingsAction.restartRequired(true, true, false, 37653, true, false, 37653),
            "password-only save keeps running listener");
    }

    private static void restartsWhenPortChanges()
    {
        check(McpSettingsAction.restartRequired(true, true, false, 37653, true, false, 37654),
            "port change restarts listener");
    }

    private static void restartsWhenServerIsStopped()
    {
        check(McpSettingsAction.restartRequired(false, true, false, 37653, true, true, 37653),
            "stopped server can start with new binding");
    }

    private static void restartsWhenDisablingServer()
    {
        check(McpSettingsAction.restartRequired(true, true, false, 37653, false, true, 37653),
            "disable stops server");
    }

    private static void check(boolean condition, String message)
    {
        if (!condition)
            throw new AssertionError(message);
    }
}
