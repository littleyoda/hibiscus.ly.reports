package de.open4me.hibiscus.reports.automation.runtime;

import de.willuhn.jameica.hbci.HBCI;
import de.willuhn.jameica.hbci.rmi.SynchronizeSchedulerService;
import de.willuhn.jameica.hbci.synchronize.SynchronizeEngine;
import de.willuhn.jameica.messaging.Message;
import de.willuhn.jameica.messaging.MessageConsumer;
import de.willuhn.jameica.messaging.QueryMessage;
import de.willuhn.jameica.system.Application;
import de.willuhn.logging.Logger;
import de.willuhn.util.ProgressMonitor;

public final class AutomationDialogGate
{
    private final Object monitor = new Object();
    private final MessageConsumer consumer = new SyncStatusConsumer();
    private boolean registered;
    private boolean engineRunning;
    private boolean schedulerRunning;
    private boolean stopping;

    public void start()
    {
        synchronized (monitor)
        {
            stopping = false;
            if (registered)
                return;
            Application.getMessagingFactory().getMessagingQueue(SynchronizeEngine.STATUS)
                .registerMessageConsumer(consumer);
            registered = true;
        }
        refreshSchedulerStatus();
    }

    public void stop()
    {
        synchronized (monitor)
        {
            stopping = true;
            engineRunning = false;
            schedulerRunning = false;
            monitor.notifyAll();
            if (!registered)
                return;
            try
            {
                Application.getMessagingFactory().getMessagingQueue(SynchronizeEngine.STATUS)
                    .unRegisterMessageConsumer(consumer);
            }
            catch (Exception e)
            {
                Logger.warn("Automation-Dialogstatus konnte nicht abgemeldet werden: " + e.getMessage());
            }
            registered = false;
        }
    }

    public void awaitDialogAllowed()
    {
        awaitDialogAllowed(null, null);
    }

    public void awaitDialogAllowed(Runnable waitingStarted, Runnable waitingFinished)
    {
        boolean waiting = false;
        while (true)
        {
            refreshSchedulerStatus();
            synchronized (monitor)
            {
                if (!isRunning() || stopping)
                    break;
                if (!waiting)
                {
                    waiting = true;
                    if (waitingStarted != null)
                        waitingStarted.run();
                }
                try
                {
                    monitor.wait(1000L);
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                    throw new AutomationCanceledException("Automation wurde unterbrochen.");
                }
            }
        }
        synchronized (monitor)
        {
            if (stopping)
                throw new AutomationCanceledException("Automation wurde beendet.");
        }
        if (waiting && waitingFinished != null)
            waitingFinished.run();
    }

    private boolean isRunning()
    {
        return engineRunning || schedulerRunning;
    }

    private void handleEngineStatus(Object data)
    {
        if (!(data instanceof Integer))
            return;
        int status = ((Integer) data).intValue();
        synchronized (monitor)
        {
            engineRunning = status == ProgressMonitor.STATUS_RUNNING;
            if (!isRunning())
                monitor.notifyAll();
        }
    }

    private void refreshSchedulerStatus()
    {
        try
        {
            SynchronizeSchedulerService scheduler = (SynchronizeSchedulerService) Application.getServiceFactory()
                .lookup(HBCI.class, "synchronizescheduler");
            setSchedulerStatus(scheduler.getStatus());
        }
        catch (Exception e)
        {
            Logger.warn("Status der Hibiscus-Scheduler-Synchronisierung konnte nicht ermittelt werden: "
                + e.getMessage());
            setSchedulerStatus(ProgressMonitor.STATUS_NONE);
        }
    }

    private void setSchedulerStatus(int status)
    {
        synchronized (monitor)
        {
            schedulerRunning = status == ProgressMonitor.STATUS_RUNNING;
            if (!isRunning())
                monitor.notifyAll();
        }
    }

    private final class SyncStatusConsumer implements MessageConsumer
    {
        @Override
        public Class[] getExpectedMessageTypes()
        {
            return new Class[] { QueryMessage.class };
        }

        @Override
        public void handleMessage(Message message) throws Exception
        {
            handleEngineStatus(((QueryMessage) message).getData());
        }

        @Override
        public boolean autoRegister()
        {
            return false;
        }
    }
}
