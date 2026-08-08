package de.open4me.hibiscus.reports.automation.runtime;

import java.util.concurrent.atomic.AtomicInteger;

final class AutomationSyncTriggerGuard
{
    private static final AtomicInteger SUPPRESSED_SYNCS = new AtomicInteger();

    private AutomationSyncTriggerGuard()
    {
    }

    static void enterSuppressedSync()
    {
        SUPPRESSED_SYNCS.incrementAndGet();
    }

    static void leaveSuppressedSync()
    {
        SUPPRESSED_SYNCS.updateAndGet(value -> Math.max(0, value - 1));
    }

    static boolean isSuppressed()
    {
        return SUPPRESSED_SYNCS.get() > 0;
    }
}
