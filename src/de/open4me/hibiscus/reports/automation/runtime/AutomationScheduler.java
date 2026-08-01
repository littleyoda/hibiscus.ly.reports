package de.open4me.hibiscus.reports.automation.runtime;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import de.open4me.hibiscus.reports.automation.model.Automation;
import de.open4me.hibiscus.reports.automation.model.AutomationRun;
import de.open4me.hibiscus.reports.automation.model.AutomationTrigger;
import de.open4me.hibiscus.reports.automation.model.MissedTriggerPolicy;
import de.open4me.hibiscus.reports.automation.model.RunStatus;
import de.open4me.hibiscus.reports.automation.sql.AutomationRepository;
import de.willuhn.logging.Logger;

public final class AutomationScheduler
{
    public static final int STARTUP_MISSED_TRIGGER_DELAY_SECONDS = 30;
    private static final int TICK_DELAY_SECONDS = 60;

    public enum MissedStartupAction
    {
        NONE,
        NACHHOLEN,
        NACHFRAGEN
    }

    private final AutomationRepository repository;
    private final AutomationDispatcher dispatcher;
    private final AutomationSchedule schedule = new AutomationSchedule();
    private ScheduledExecutorService executor;

    public AutomationScheduler(AutomationRepository repository, AutomationDispatcher dispatcher)
    {
        this.repository = repository;
        this.dispatcher = dispatcher;
    }

    public synchronized void start()
    {
        if (executor != null)
            return;
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "hibiscus-automation-scheduler");
            thread.setDaemon(true);
            return thread;
        });
        LocalDateTime startedAt = LocalDateTime.now();
        executor.schedule(() -> startAfterStartupDelaySafely(startedAt), STARTUP_MISSED_TRIGGER_DELAY_SECONDS,
            TimeUnit.SECONDS);
    }

    public void handleMissedTriggersOnStart() throws Exception
    {
        LocalDateTime now = LocalDateTime.now();
        handleMissedTriggersOnStart(now, now);
    }

    void handleMissedTriggersOnStart(LocalDateTime dueAt, LocalDateTime now) throws Exception
    {
        List<AutomationTrigger> dueTriggers = repository.listDueTriggers(dueAt);
        if (dueTriggers.isEmpty())
        {
            Logger.debug("hibiscus.ly.reports automation: keine verpassten Zeittrigger beim Start gefunden.");
            return;
        }
        Logger.info("hibiscus.ly.reports automation: " + dueTriggers.size()
            + " faellige Zeittrigger beim Start gefunden.");
        for (AutomationTrigger trigger : dueTriggers)
        {
            Automation automation = repository.getAutomation(trigger.automationId());
            if (automation == null)
            {
                Logger.warn("hibiscus.ly.reports automation: faelliger Zeittrigger " + trigger.id()
                    + " verweist auf unbekannte Automation " + trigger.automationId() + ".");
                continue;
            }
            LocalDateTime next = schedule.next(trigger.schedule(), now);
            MissedStartupAction action = missedStartupAction(automation, trigger, dueAt);
            Logger.debug("hibiscus.ly.reports automation: verpasster Zeittrigger automation=" + automation.name()
                + ", automationActive=" + automation.active() + ", triggerActive=" + trigger.active()
                + ", nextRun=" + trigger.nextRun() + ", policy=" + automation.missedTriggerPolicy().value()
                + ", action=" + action + ", naechsterLauf=" + next + ".");
            if (action == MissedStartupAction.NACHHOLEN)
            {
                dispatcher.dispatch(automation, trigger, "nachgeholt", false, false);
                repository.saveTrigger(trigger.withLastRun(now).withNextRun(next));
                continue;
            }
            if (action == MissedStartupAction.NACHFRAGEN)
            {
                waitingDecision(automation, trigger, now);
                repository.saveTrigger(trigger.withNextRun(next));
                continue;
            }
            repository.saveTrigger(trigger.withNextRun(next));
        }
    }

    public static MissedStartupAction missedStartupAction(Automation automation, AutomationTrigger trigger,
                                                          LocalDateTime now)
    {
        if (automation == null || trigger == null || now == null)
            return MissedStartupAction.NONE;
        if (!automation.active() || !trigger.active())
            return MissedStartupAction.NONE;
        if (trigger.nextRun() == null || trigger.nextRun().isAfter(now))
            return MissedStartupAction.NONE;
        if (automation.missedTriggerPolicy() == MissedTriggerPolicy.NACHHOLEN)
            return MissedStartupAction.NACHHOLEN;
        if (automation.missedTriggerPolicy() == MissedTriggerPolicy.NACHFRAGEN)
            return MissedStartupAction.NACHFRAGEN;
        return MissedStartupAction.NONE;
    }

    public synchronized void stop()
    {
        if (executor != null)
            executor.shutdownNow();
        executor = null;
    }

    private void tickSafely()
    {
        try
        {
            tick();
        }
        catch (Exception e)
        {
            Logger.error("Automation-Scheduler fehlgeschlagen", e);
        }
    }

    private void startAfterStartupDelaySafely(LocalDateTime startedAt)
    {
        try
        {
            handleMissedTriggersOnStart(startedAt, LocalDateTime.now());
        }
        catch (Exception e)
        {
            Logger.error("Verpasste Automation-Trigger konnten beim Start nicht verarbeitet werden", e);
        }
        finally
        {
            startTicks();
        }
    }

    private synchronized void startTicks()
    {
        if (executor == null || executor.isShutdown())
            return;
        executor.scheduleWithFixedDelay(this::tickSafely, 0, TICK_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    void tick() throws Exception
    {
        LocalDateTime now = LocalDateTime.now();
        for (AutomationTrigger trigger : repository.listDueTriggers(now))
        {
            Automation automation = repository.getAutomation(trigger.automationId());
            if (automation == null || !automation.active())
                continue;
            dispatcher.dispatch(automation, trigger, "zeitgesteuert", false, false);
            LocalDateTime next = schedule.next(trigger.schedule(), now);
            repository.saveTrigger(trigger.withLastRun(now).withNextRun(next));
        }
    }

    private void waitingDecision(Automation automation, AutomationTrigger trigger, LocalDateTime now) throws Exception
    {
        AutomationRun run = repository.createRun(automation.id(), trigger.id(), "verpasst", false);
        String message = "Verpasster Zeittrigger wartet auf Nutzerentscheidung.";
        repository.addLog(run.id(), "warn", message);
        repository.createDecision(run.id(), "missed-trigger",
            "{\"automationId\":\"" + json(automation.id()) + "\",\"triggerId\":\"" + json(trigger.id())
                + "\",\"missedAt\":\"" + json(String.valueOf(trigger.nextRun())) + "\",\"detectedAt\":\""
                + json(String.valueOf(now)) + "\"}");
        repository.updateRunStatus(run.id(), RunStatus.WARTET, message, "", false);
    }

    private static String json(String value)
    {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
