package de.open4me.hibiscus.reports.automation.runtime;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import de.open4me.hibiscus.reports.automation.model.Automation;
import de.open4me.hibiscus.reports.automation.model.AutomationTrigger;
import de.open4me.hibiscus.reports.automation.model.AutomationTriggerTypes;
import de.open4me.hibiscus.reports.automation.sql.AutomationRepository;
import de.open4me.hibiscus.reports.data.HibiscusReportTransactionProvider;
import de.open4me.hibiscus.reports.model.ReportTransaction;
import de.willuhn.jameica.hbci.messaging.ImportMessage;
import de.willuhn.jameica.hbci.rmi.Umsatz;
import de.willuhn.jameica.messaging.Message;
import de.willuhn.jameica.messaging.MessageConsumer;
import de.willuhn.jameica.messaging.QueryMessage;
import de.willuhn.jameica.system.Application;
import de.willuhn.logging.Logger;

public final class AutomationTransactionTriggerListener
{
    private static final String STORE_QUEUE = "hibiscus.dbobject.store";

    private final AutomationRepository repository;
    private final AutomationDispatcher dispatcher;
    private final MessageConsumer importConsumer = new ImportConsumer();
    private final MessageConsumer storeConsumer = new StoreConsumer();
    private boolean registered;

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
        }
        catch (Exception e)
        {
            Logger.warn("Umsatz-Trigger-Listener konnte nicht abgemeldet werden: " + e.getMessage());
        }
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

            ReportTransaction transaction = HibiscusReportTransactionProvider.toReportTransaction(umsatz);
            LocalDateTime now = LocalDateTime.now();
            for (AutomationTrigger trigger : triggers)
            {
                if (!repository.recordTriggerEvent(trigger.id(), AutomationTriggerTypes.TRANSACTION_NEW,
                    transactionId))
                    continue;
                Automation automation = repository.getAutomation(trigger.automationId());
                if (automation == null || !automation.active())
                    continue;
                dispatcher.dispatch(automation, trigger, "umsatz", false, false, null,
                    Map.of("umsatz", transaction));
                repository.saveTrigger(trigger.withLastRun(now));
            }
        }
        catch (Exception e)
        {
            Logger.error("Umsatz-Trigger konnte nicht gestartet werden", e);
        }
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
}
