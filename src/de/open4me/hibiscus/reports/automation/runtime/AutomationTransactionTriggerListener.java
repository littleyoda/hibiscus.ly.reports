package de.open4me.hibiscus.reports.automation.runtime;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import de.open4me.hibiscus.reports.automation.model.Automation;
import de.open4me.hibiscus.reports.automation.model.AutomationTrigger;
import de.open4me.hibiscus.reports.automation.model.AutomationTriggerTypes;
import de.open4me.hibiscus.reports.automation.sql.AutomationRepository;
import de.open4me.hibiscus.reports.data.HibiscusReportTransactionProvider;
import de.open4me.hibiscus.reports.model.ReportTransaction;
import de.willuhn.jameica.hbci.Settings;
import de.willuhn.jameica.hbci.messaging.ImportMessage;
import de.willuhn.jameica.hbci.rmi.Umsatz;
import de.willuhn.jameica.hbci.synchronize.SynchronizeBackend;
import de.willuhn.jameica.hbci.synchronize.SynchronizeEngine;
import de.willuhn.jameica.messaging.Message;
import de.willuhn.jameica.messaging.MessageConsumer;
import de.willuhn.jameica.messaging.QueryMessage;
import de.willuhn.jameica.system.Application;
import de.willuhn.logging.Logger;
import de.willuhn.util.ProgressMonitor;

public final class AutomationTransactionTriggerListener
{
    private static final String STORE_QUEUE = "hibiscus.dbobject.store";

    private final AutomationRepository repository;
    private final AutomationDispatcher dispatcher;
    private final MessageConsumer importConsumer = new ImportConsumer();
    private final MessageConsumer storeConsumer = new StoreConsumer();
    private final MessageConsumer engineStatusConsumer = new StatusConsumer(true);
    private final MessageConsumer backendStatusConsumer = new StatusConsumer(false);
    private final ScheduledExecutorService fallbackExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "hibiscus-automation-transaction-batch");
        thread.setDaemon(true);
        return thread;
    });
    private boolean registered;
    private boolean engineRunning;
    private boolean syncing;
    private ScheduledFuture<?> fallbackFlush;

    public AutomationTransactionTriggerListener(AutomationRepository repository, AutomationDispatcher dispatcher)
    {
        this.repository = repository;
        this.dispatcher = dispatcher;
    }

    public synchronized void start()
    {
        if (registered)
            return;
        Application.getMessagingFactory().registerMessageConsumer(importConsumer);
        Application.getMessagingFactory().getMessagingQueue(STORE_QUEUE).registerMessageConsumer(storeConsumer);
        Application.getMessagingFactory().getMessagingQueue(SynchronizeEngine.STATUS)
            .registerMessageConsumer(engineStatusConsumer);
        Application.getMessagingFactory().getMessagingQueue(SynchronizeBackend.QUEUE_STATUS)
            .registerMessageConsumer(backendStatusConsumer);
        try
        {
            repository.resetQueuedTransactionEvents();
            scheduleFallbackFlush();
        }
        catch (Exception e)
        {
            Logger.error("Offene Umsatz-Trigger konnten beim Start nicht vorbereitet werden", e);
        }
        registered = true;
    }

    public synchronized void stop()
    {
        if (!registered)
            return;
        try
        {
            Application.getMessagingFactory().unRegisterMessageConsumer(importConsumer);
            Application.getMessagingFactory().getMessagingQueue(STORE_QUEUE).unRegisterMessageConsumer(storeConsumer);
            Application.getMessagingFactory().getMessagingQueue(SynchronizeEngine.STATUS)
                .unRegisterMessageConsumer(engineStatusConsumer);
            Application.getMessagingFactory().getMessagingQueue(SynchronizeBackend.QUEUE_STATUS)
                .unRegisterMessageConsumer(backendStatusConsumer);
        }
        catch (Exception e)
        {
            Logger.warn("Umsatz-Trigger-Listener konnte nicht abgemeldet werden: " + e.getMessage());
        }
        if (fallbackFlush != null)
            fallbackFlush.cancel(false);
        registered = false;
    }

    void handleTransaction(Umsatz umsatz)
    {
        if (umsatz == null)
            return;
        try
        {
            String transactionId = umsatz.getID();
            if (transactionId == null || transactionId.isBlank())
                return;
            List<AutomationTrigger> triggers = repository.listActiveTriggersByType(
                AutomationTriggerTypes.TRANSACTION_NEW);
            if (triggers.isEmpty())
                return;

            for (AutomationTrigger trigger : triggers)
            {
                if (repository.recordPendingTransactionEvent(trigger.id(), transactionId) && !isSyncing())
                    scheduleFallbackFlush();
            }
        }
        catch (Exception e)
        {
            Logger.error("Umsatz-Trigger konnte nicht gestartet werden", e);
        }
    }

    void handleStatus(boolean engineStatus, int status)
    {
        boolean completed = false;
        synchronized (this)
        {
            if (status == ProgressMonitor.STATUS_RUNNING)
                syncing = true;
            if (engineStatus)
            {
                engineRunning = status == ProgressMonitor.STATUS_RUNNING;
                completed = status == ProgressMonitor.STATUS_DONE;
            }
            else if (!engineRunning)
            {
                completed = status == ProgressMonitor.STATUS_DONE;
            }
            if (completed)
                syncing = false;
        }
        if (completed)
            flushBatches();
    }

    private synchronized boolean isSyncing()
    {
        return syncing;
    }

    private synchronized void scheduleFallbackFlush()
    {
        if (fallbackFlush != null && !fallbackFlush.isDone())
            return;
        fallbackFlush = fallbackExecutor.schedule(this::flushBatches, 2, TimeUnit.SECONDS);
    }

    private void flushBatches()
    {
        try
        {
            List<AutomationTrigger> triggers = repository.listActiveTriggersByType(
                AutomationTriggerTypes.TRANSACTION_NEW);
            if (triggers.isEmpty())
                return;
            LocalDateTime now = LocalDateTime.now();
            for (AutomationTrigger trigger : triggers)
                flushBatch(trigger, now);
        }
        catch (Exception e)
        {
            Logger.error("Umsatz-Trigger-Batches konnten nicht gestartet werden", e);
        }
    }

    private void flushBatch(AutomationTrigger trigger, LocalDateTime now) throws Exception
    {
        List<String> transactionIds = repository.listPendingTransactionEventKeys(trigger.id());
        if (transactionIds.isEmpty())
            return;
        Automation automation = repository.getAutomation(trigger.automationId());
        if (automation == null || !automation.active())
            return;
        List<ReportTransaction> transactions = new ArrayList<>();
        for (String transactionId : transactionIds)
        {
            Umsatz umsatz = Settings.getDBService().createObject(Umsatz.class, transactionId);
            if (umsatz != null)
                transactions.add(HibiscusReportTransactionProvider.toReportTransaction(umsatz));
        }
        if (transactions.isEmpty())
            return;

        repository.updateTransactionEventStatus(trigger.id(), transactionIds, AutomationRepository.EVENT_QUEUED);
        try
        {
            dispatcher.dispatchQueued(automation, trigger, "umsatz", false, false, () -> {
                try
                {
                    repository.updateTransactionEventStatus(trigger.id(), transactionIds,
                        AutomationRepository.EVENT_PROCESSED);
                }
                catch (Exception e)
                {
                    Logger.error("Umsatz-Trigger-Batch konnte nicht als verarbeitet markiert werden", e);
                }
            }, Map.of("neueUmsaetze", transactions, "umsatz", transactions.get(0)));
        }
        catch (Exception e)
        {
            repository.updateTransactionEventStatus(trigger.id(), transactionIds, AutomationRepository.EVENT_PENDING);
            throw e;
        }
        repository.saveTrigger(trigger.withLastRun(now));
    }

    private final class ImportConsumer implements MessageConsumer
    {
        @Override
        public Class[] getExpectedMessageTypes()
        {
            return new Class[] { ImportMessage.class };
        }

        @Override
        public void handleMessage(Message message)
        {
            Object object = ((ImportMessage) message).getObject();
            if (object instanceof Umsatz umsatz)
                handleTransaction(umsatz);
        }

        @Override
        public boolean autoRegister()
        {
            return false;
        }
    }

    private final class StoreConsumer implements MessageConsumer
    {
        @Override
        public Class[] getExpectedMessageTypes()
        {
            return new Class[] { QueryMessage.class };
        }

        @Override
        public void handleMessage(Message message)
        {
            Object object = ((QueryMessage) message).getData();
            if (object instanceof Umsatz umsatz)
                handleTransaction(umsatz);
        }

        @Override
        public boolean autoRegister()
        {
            return false;
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
