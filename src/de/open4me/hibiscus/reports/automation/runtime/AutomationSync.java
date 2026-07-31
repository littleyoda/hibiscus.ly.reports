package de.open4me.hibiscus.reports.automation.runtime;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.openjdk.nashorn.api.scripting.ScriptObjectMirror;

import de.open4me.hibiscus.reports.data.ReportAccountsProxy;
import de.open4me.hibiscus.reports.model.ReportAccount;
import de.willuhn.jameica.hbci.gui.filter.KontoFilter;
import de.willuhn.jameica.hbci.rmi.Konto;
import de.willuhn.jameica.hbci.server.KontoUtil;
import de.willuhn.jameica.hbci.synchronize.Synchronization;
import de.willuhn.jameica.hbci.synchronize.SynchronizeBackend;
import de.willuhn.jameica.hbci.synchronize.SynchronizeEngine;
import de.willuhn.jameica.hbci.synchronize.jobs.SynchronizeJob;
import de.willuhn.jameica.hbci.synchronize.jobs.SynchronizeJobKontoauszug;
import de.willuhn.jameica.messaging.Message;
import de.willuhn.jameica.messaging.MessageConsumer;
import de.willuhn.jameica.messaging.QueryMessage;
import de.willuhn.jameica.system.Application;
import de.willuhn.jameica.services.BeanService;
import de.willuhn.logging.Logger;
import de.willuhn.util.ApplicationException;
import de.willuhn.util.ProgressMonitor;

public final class AutomationSync
{
    private final AutomationContext context;
    private final SyncLog log;
    private final AutomationDialogGate dialogGate;
    private final Runnable invalidateCaches;

    public AutomationSync(AutomationContext context, SyncLog log, AutomationDialogGate dialogGate,
                          Runnable invalidateCaches)
    {
        this.context = context;
        this.log = log;
        this.dialogGate = dialogGate;
        this.invalidateCaches = invalidateCaches;
    }

    public Object alle() throws Exception
    {
        return run(() -> createAllSyncs(), "alle Konten");
    }

    public Object starten(Object... values) throws Exception
    {
        List<ReportAccount> accounts = accounts(values);
        if (accounts.isEmpty())
            throw new IllegalArgumentException("sync.starten erwartet mindestens ein Kontoobjekt.");
        return run(() -> createAccountSyncs(accounts), accountSummary(accounts));
    }

    private Object run(Callable<List<Synchronization>> syncFactory, String summary) throws Exception
    {
        if (!context.getTestlauf() && context.getWriteAllowed())
        {
            dialogGate.awaitDialogAllowed(
                () -> log.info("Synchronisierung wartet auf Ende einer laufenden Hibiscus-Synchronisierung."),
                () -> log.info("Synchronisierung wird fortgesetzt."));
        }

        List<Synchronization> syncs = syncFactory.call();
        int jobs = jobCount(syncs);
        if (jobs == 0)
        {
            log.info("Keine Synchronisierungsjobs gefunden fuer " + summary + ".");
            return result(false, context.getTestlauf(), summary, jobs, "keine_jobs");
        }

        if (context.getTestlauf() || !context.getWriteAllowed())
        {
            log.info("Testlauf: Synchronisierung geplant fuer " + summary + " (" + jobs + " Jobs).");
            return result(false, true, summary, jobs, "testlauf");
        }

        SyncWaiter waiter = new SyncWaiter();
        waiter.register();
        try
        {
            log.info("Starte Synchronisierung fuer " + summary + " (" + jobs + " Jobs).");
            new de.willuhn.jameica.hbci.gui.action.Synchronize().handleAction(syncs);
            int status = waiter.await();
            if (status == ProgressMonitor.STATUS_CANCEL)
                throw new AutomationCanceledException("Synchronisierung wurde abgebrochen.");
            if (status == ProgressMonitor.STATUS_ERROR)
                throw new ApplicationException("Synchronisierung wurde mit Fehler beendet.");
            if (invalidateCaches != null)
                invalidateCaches.run();
            log.info("Synchronisierung beendet fuer " + summary + ".");
            return result(true, false, summary, jobs, "beendet");
        }
        finally
        {
            waiter.unregister();
        }
    }

    private static List<Synchronization> createAllSyncs() throws ApplicationException
    {
        BeanService service = Application.getBootLoader().getBootable(BeanService.class);
        SynchronizeEngine engine = service.get(SynchronizeEngine.class);
        List<Synchronization> result = new ArrayList<>();
        try
        {
            for (Konto konto : KontoUtil.getKonten(KontoFilter.SYNCED))
                addForcedAccountSync(result, engine, konto, false);
        }
        catch (RemoteException e)
        {
            throw new IllegalStateException("Konten konnten nicht geladen werden", e);
        }
        return result;
    }

    private List<Synchronization> createAccountSyncs(List<ReportAccount> accounts) throws Exception
    {
        BeanService service = Application.getBootLoader().getBootable(BeanService.class);
        SynchronizeEngine engine = service.get(SynchronizeEngine.class);
        List<Synchronization> result = new ArrayList<>();
        Map<String, Konto> hibiscusAccounts = hibiscusAccountsById();

        for (ReportAccount account : accounts)
        {
            Konto konto = hibiscusAccounts.get(account.getId());
            if (konto == null)
                throw new IllegalArgumentException("Konto wurde in Hibiscus nicht gefunden: " + account.getName());

            if (!account.getAktiv())
            {
                log.warn("Konto wird nicht synchronisiert, weil es deaktiviert ist: " + account.getName());
                continue;
            }

            addForcedAccountSync(result, engine, konto, true);
        }

        return result;
    }

    private static void addForcedAccountSync(List<Synchronization> result, SynchronizeEngine engine, Konto konto,
                                             boolean failIfUnsupported) throws ApplicationException
    {
        SynchronizeBackend backend;
        try
        {
            backend = engine.getBackend(SynchronizeJobKontoauszug.class, konto);
        }
        catch (ApplicationException e)
        {
            if (failIfUnsupported)
                throw e;
            return;
        }

        SynchronizeJob job = backend.create(SynchronizeJobKontoauszug.class, konto);
        job.setContext(SynchronizeJob.CTX_ENTITY, konto);
        job.setContext(SynchronizeJobKontoauszug.CTX_FORCE_SALDO, true);
        job.setContext(SynchronizeJobKontoauszug.CTX_FORCE_UMSATZ, true);
        addSync(result, backend, job);
    }

    private static void addSync(List<Synchronization> result, SynchronizeBackend backend, SynchronizeJob job)
    {
        if (job == null || !job.isRecurring())
            return;
        Synchronization sync = new Synchronization();
        sync.setBackend(backend);
        sync.setJobs(List.of(job));
        result.add(sync);
    }

    private static Map<String, Konto> hibiscusAccountsById() throws RemoteException
    {
        Map<String, Konto> result = new LinkedHashMap<>();
        for (Konto konto : KontoUtil.getKonten(KontoFilter.ALL))
            result.put(konto.getID(), konto);
        return result;
    }

    private static int jobCount(List<Synchronization> syncs)
    {
        int count = 0;
        for (Synchronization sync : syncs)
            count += sync.getJobs().size();
        return count;
    }

    private static List<ReportAccount> accounts(Object... values)
    {
        List<ReportAccount> result = new ArrayList<>();
        if (values == null)
            return result;
        for (Object value : values)
            addAccounts(result, value);
        return result;
    }

    private static void addAccounts(List<ReportAccount> result, Object value)
    {
        if (value == null)
            return;
        if (value instanceof ReportAccount account)
        {
            result.add(account);
            return;
        }
        if (value instanceof ReportAccountsProxy proxy)
        {
            result.addAll(proxy.getAktive());
            return;
        }
        if (value instanceof ScriptObjectMirror mirror && mirror.isArray())
        {
            for (Object item : mirror.values())
                addAccounts(result, item);
            return;
        }
        if (value instanceof Iterable<?> iterable)
        {
            for (Object item : iterable)
                addAccounts(result, item);
            return;
        }
        if (value instanceof Object[] array)
        {
            for (Object item : array)
                addAccounts(result, item);
            return;
        }
        throw new IllegalArgumentException("sync.starten erwartet Kontoobjekte.");
    }

    private static String accountSummary(List<ReportAccount> accounts)
    {
        if (accounts.size() == 1)
            return accounts.get(0).getName();
        return accounts.size() + " Konten";
    }

    private static Map<String, Object> result(boolean started, boolean testRun, String accounts, int jobs,
                                              String status)
    {
        return Map.of("gestartet", started, "testlauf", testRun, "konten", accounts, "jobs", jobs, "status",
            status);
    }

    private static final class SyncWaiter
    {
        private final CountDownLatch latch = new CountDownLatch(1);
        private final AtomicInteger status = new AtomicInteger(ProgressMonitor.STATUS_NONE);
        private final MessageConsumer consumer = new MessageConsumer()
        {
            @Override
            public Class[] getExpectedMessageTypes()
            {
                return new Class[] { QueryMessage.class };
            }

            @Override
            public void handleMessage(Message message)
            {
                Object data = ((QueryMessage) message).getData();
                if (!(data instanceof Integer))
                    return;
                int value = ((Integer) data).intValue();
                status.set(value);
                if (value == ProgressMonitor.STATUS_DONE || value == ProgressMonitor.STATUS_ERROR
                    || value == ProgressMonitor.STATUS_CANCEL)
                    latch.countDown();
            }

            @Override
            public boolean autoRegister()
            {
                return false;
            }
        };

        void register()
        {
            Application.getMessagingFactory().getMessagingQueue(SynchronizeEngine.STATUS)
                .registerMessageConsumer(consumer);
        }

        int await()
        {
            try
            {
                latch.await();
                return status.get();
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                throw new AutomationCanceledException("Synchronisierung wurde unterbrochen.");
            }
        }

        void unregister()
        {
            try
            {
                Application.getMessagingFactory().getMessagingQueue(SynchronizeEngine.STATUS)
                    .unRegisterMessageConsumer(consumer);
            }
            catch (Exception e)
            {
                Logger.warn("Sync-Status konnte nicht abgemeldet werden: " + e.getMessage());
            }
        }
    }
}
