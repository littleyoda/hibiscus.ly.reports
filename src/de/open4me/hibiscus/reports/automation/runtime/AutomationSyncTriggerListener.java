package de.open4me.hibiscus.reports.automation.runtime;

import java.time.LocalDateTime;
import java.util.List;

import de.open4me.hibiscus.reports.automation.model.Automation;
import de.open4me.hibiscus.reports.automation.model.AutomationTrigger;
import de.open4me.hibiscus.reports.automation.model.AutomationTriggerTypes;
import de.open4me.hibiscus.reports.automation.sql.AutomationRepository;
import de.willuhn.jameica.hbci.synchronize.SynchronizeBackend;
import de.willuhn.jameica.hbci.synchronize.SynchronizeEngine;
import de.willuhn.jameica.messaging.Message;
import de.willuhn.jameica.messaging.MessageConsumer;
import de.willuhn.jameica.messaging.QueryMessage;
import de.willuhn.jameica.system.Application;
import de.willuhn.logging.Logger;
import de.willuhn.util.ProgressMonitor;

public final class AutomationSyncTriggerListener
{
    private final AutomationRepository repository;
    private final AutomationDispatcher dispatcher;
    private final MessageConsumer engineConsumer = new StatusConsumer(true);
    private final MessageConsumer backendConsumer = new StatusConsumer(false);
    private boolean registered;
    private boolean engineRunning;

    public AutomationSyncTriggerListener(AutomationRepository repository, AutomationDispatcher dispatcher)
    {
        this.repository = repository;
        this.dispatcher = dispatcher;
    }

    public synchronized void start()
    {
        if (registered)
            return;
        Application.getMessagingFactory().getMessagingQueue(SynchronizeEngine.STATUS)
            .registerMessageConsumer(engineConsumer);
        Application.getMessagingFactory().getMessagingQueue(SynchronizeBackend.QUEUE_STATUS)
            .registerMessageConsumer(backendConsumer);
        registered = true;
    }

    public synchronized void stop()
    {
        engineRunning = false;
        if (!registered)
            return;
        try
        {
            Application.getMessagingFactory().getMessagingQueue(SynchronizeEngine.STATUS)
                .unRegisterMessageConsumer(engineConsumer);
            Application.getMessagingFactory().getMessagingQueue(SynchronizeBackend.QUEUE_STATUS)
                .unRegisterMessageConsumer(backendConsumer);
        }
        catch (Exception e)
        {
            Logger.warn("Sync-Trigger-Listener konnte nicht abgemeldet werden: " + e.getMessage());
        }
        registered = false;
    }

    void handleStatus(boolean engineStatus, int status)
    {
        boolean completed = false;
        synchronized (this)
        {
            if (engineStatus)
            {
                engineRunning = status == ProgressMonitor.STATUS_RUNNING;
                completed = status == ProgressMonitor.STATUS_DONE;
            }
            else if (!engineRunning)
            {
                completed = status == ProgressMonitor.STATUS_DONE;
            }
        }

        if (completed)
            triggerAfterSync();
    }

    private void triggerAfterSync()
    {
        if (AutomationSyncTriggerGuard.isSuppressed())
        {
            Logger.info("hibiscus.ly.reports automation: Sync-Trigger nach automationseigener Synchronisierung ignoriert.");
            return;
        }
        try
        {
            List<AutomationTrigger> triggers = repository.listActiveTriggersByType(AutomationTriggerTypes.SYNC_AFTER);
            if (triggers.isEmpty())
                return;
            LocalDateTime now = LocalDateTime.now();
            Logger.info("hibiscus.ly.reports automation: " + triggers.size()
                + " Sync-Trigger nach erfolgreicher Synchronisierung gefunden.");
            for (AutomationTrigger trigger : triggers)
            {
                Automation automation = repository.getAutomation(trigger.automationId());
                if (automation == null || !automation.active())
                    continue;
                dispatcher.dispatch(automation, trigger, "synchronisierung", false, false);
                repository.saveTrigger(trigger.withLastRun(now));
            }
        }
        catch (Exception e)
        {
            Logger.error("Sync-Trigger konnten nach Synchronisierung nicht gestartet werden", e);
        }
    }

    private final class StatusConsumer implements MessageConsumer
    {
        private final boolean engineStatus;

        private StatusConsumer(boolean engineStatus)
        {
            this.engineStatus = engineStatus;
        }

        @Override
        public Class[] getExpectedMessageTypes()
        {
            return new Class[] { QueryMessage.class };
        }

        @Override
        public void handleMessage(Message message)
        {
            Object data = ((QueryMessage) message).getData();
            if (data instanceof Integer status)
                handleStatus(engineStatus, status.intValue());
        }

        @Override
        public boolean autoRegister()
        {
            return false;
        }
    }
}
