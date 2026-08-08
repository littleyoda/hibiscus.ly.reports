package de.open4me.hibiscus.reports.automation.runtime;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.openjdk.nashorn.api.scripting.ScriptObjectMirror;

import de.open4me.hibiscus.reports.data.ReportAccountsProxy;
import de.open4me.hibiscus.reports.model.ReportAccount;
import de.willuhn.jameica.gui.GUI;
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

    public Object alle(Object... values) throws Exception
    {
        SyncOptions options = options(values);
        if (!options.explicit())
            return alle();
        return runSequential(createAllTargets(), "alle Konten", options);
    }

    public Object starten(Object... values) throws Exception
    {
        SyncOptions options = options(values);
        List<ReportAccount> accounts = accounts(valuesWithoutOptions(values, options));
        if (accounts.isEmpty())
            throw new IllegalArgumentException("sync.starten erwartet mindestens ein Kontoobjekt.");
        if (!options.explicit())
            return run(() -> createAccountSyncs(accounts), accountSummary(accounts));
        return runSequential(accountTargets(accounts), accountSummary(accounts), options);
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
        AutomationSyncTriggerGuard.enterSuppressedSync();
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
            AutomationSyncTriggerGuard.leaveSuppressedSync();
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

    private static List<SyncTarget> createAllTargets() throws ApplicationException
    {
        BeanService service = Application.getBootLoader().getBootable(BeanService.class);
        SynchronizeEngine engine = service.get(SynchronizeEngine.class);
        List<SyncTarget> result = new ArrayList<>();
        try
        {
            for (Konto konto : KontoUtil.getKonten(KontoFilter.SYNCED))
                result.add(new SyncTarget(accountName(konto), () -> {
                    List<Synchronization> syncs = new ArrayList<>();
                    addForcedAccountSync(syncs, engine, konto, false);
                    return syncs;
                }));
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

    private List<SyncTarget> accountTargets(List<ReportAccount> accounts)
    {
        List<SyncTarget> result = new ArrayList<>();
        for (ReportAccount account : accounts)
            result.add(new SyncTarget(accountName(account), () -> createAccountSyncs(List.of(account))));
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

    private Object runSequential(List<SyncTarget> targets, String summary, SyncOptions options) throws Exception
    {
        if (!context.getTestlauf() && context.getWriteAllowed())
        {
            dialogGate.awaitDialogAllowed(
                () -> log.info("Synchronisierung wartet auf Ende einer laufenden Hibiscus-Synchronisierung."),
                () -> log.info("Synchronisierung wird fortgesetzt."));
        }

        SyncSummary result = new SyncSummary(summary, context.getTestlauf() || !context.getWriteAllowed());
        if (targets.isEmpty())
        {
            log.info("Keine Synchronisierungsjobs gefunden fuer " + summary + ".");
            return result.toMap("keine_jobs");
        }

        for (SyncTarget target : targets)
        {
            try
            {
                List<Synchronization> syncs = target.syncFactory().call();
                int jobs = jobCount(syncs);
                result.jobs += jobs;
                if (jobs == 0)
                {
                    log.info("Keine Synchronisierungsjobs gefunden fuer " + target.name() + ".");
                    continue;
                }
                if (context.getTestlauf() || !context.getWriteAllowed())
                {
                    log.info("Testlauf: Synchronisierung geplant fuer " + target.name() + " (" + jobs + " Jobs).");
                    continue;
                }
                executeSyncs(syncs, target.name(), jobs);
                result.started = true;
                result.successful++;
            }
            catch (AutomationCanceledException e)
            {
                throw e;
            }
            catch (Exception e)
            {
                SyncError error = new SyncError(target.name(), message(e));
                if (options.strategy() == ErrorStrategy.STOPPEN)
                    throw e;
                result.failed++;
                result.errors.add(error);
                log.error("Synchronisierung fehlgeschlagen fuer " + target.name() + ": " + error.message());
                if (options.strategy() == ErrorStrategy.NACHFRAGEN && !confirmContinue(error))
                {
                    result.aborted = true;
                    break;
                }
            }
        }

        if (result.started && invalidateCaches != null)
            invalidateCaches.run();

        if (context.getTestlauf() || !context.getWriteAllowed())
            return result.toMap("testlauf");
        if (result.aborted)
            return result.toMap("abgebrochen");
        if (!result.errors.isEmpty())
            return result.toMap("fehler");
        if (!result.started && result.jobs == 0)
            return result.toMap("keine_jobs");
        log.info("Synchronisierung beendet fuer " + summary + ".");
        return result.toMap("beendet");
    }

    private void executeSyncs(List<Synchronization> syncs, String summary, int jobs) throws Exception
    {
        SyncWaiter waiter = new SyncWaiter();
        waiter.register();
        AutomationSyncTriggerGuard.enterSuppressedSync();
        try
        {
            log.info("Starte Synchronisierung fuer " + summary + " (" + jobs + " Jobs).");
            new de.willuhn.jameica.hbci.gui.action.Synchronize().handleAction(syncs);
            int status = waiter.await();
            if (status == ProgressMonitor.STATUS_CANCEL)
                throw new AutomationCanceledException("Synchronisierung wurde abgebrochen.");
            if (status == ProgressMonitor.STATUS_ERROR)
                throw new ApplicationException("Synchronisierung wurde mit Fehler beendet.");
        }
        finally
        {
            AutomationSyncTriggerGuard.leaveSuppressedSync();
            waiter.unregister();
        }
    }

    private boolean confirmContinue(SyncError error)
    {
        dialogGate.awaitDialogAllowed(
            () -> log.info("Fehler-Rueckfrage wartet auf Ende einer laufenden Hibiscus-Synchronisierung."),
            () -> log.info("Fehler-Rueckfrage wird angezeigt."));
        AtomicReference<Boolean> result = new AtomicReference<>(false);
        GUI.getDisplay().syncExec(() -> {
            try
            {
                result.set(Application.getCallback().askUser("Kontoabruf fuer " + error.account()
                    + " ist fehlgeschlagen: " + error.message() + "\n\nMit dem naechsten Konto fortsetzen?"));
            }
            catch (Exception e)
            {
                result.set(false);
            }
        });
        log.info("Fehler-Rueckfrage fuer " + error.account() + ": " + result.get());
        return result.get();
    }

    static List<ReportAccount> accounts(Object... values)
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

    static SyncOptions options(Object... values)
    {
        if (values == null || values.length == 0 || !isOptions(values[values.length - 1]))
            return new SyncOptions(ErrorStrategy.STOPPEN, false);
        Object raw = values[values.length - 1];
        Object value = option(raw, "beiFehler");
        if (value == null)
            return new SyncOptions(ErrorStrategy.STOPPEN, true);
        return new SyncOptions(ErrorStrategy.parse(value.toString()), true);
    }

    static Object[] valuesWithoutOptions(Object[] values, SyncOptions options)
    {
        if (values == null || values.length == 0 || !options.explicit())
            return values;
        Object[] copy = new Object[values.length - 1];
        System.arraycopy(values, 0, copy, 0, copy.length);
        return copy;
    }

    private static boolean isOptions(Object value)
    {
        if (value instanceof Map<?, ?> map)
            return map.containsKey("beiFehler");
        return value instanceof ScriptObjectMirror mirror && !mirror.isArray() && mirror.containsKey("beiFehler");
    }

    private static Object option(Object value, String key)
    {
        if (value instanceof Map<?, ?> map)
            return map.get(key);
        if (value instanceof ScriptObjectMirror mirror)
            return mirror.get(key);
        return null;
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

    private static String accountName(Konto konto)
    {
        if (konto == null)
            return "Konto";
        try
        {
            String name = konto.getBezeichnung();
            return name == null || name.isBlank() ? "Konto " + konto.getID() : name;
        }
        catch (Exception e)
        {
            return "Konto";
        }
    }

    private static String accountName(ReportAccount account)
    {
        String name = account == null ? null : account.getName();
        return name == null || name.isBlank() ? "Konto" : name;
    }

    private static String message(Throwable error)
    {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getName() : message;
    }

    enum ErrorStrategy
    {
        STOPPEN("stoppen"),
        FORTSETZEN("fortsetzen"),
        NACHFRAGEN("nachfragen");

        private final String value;

        ErrorStrategy(String value)
        {
            this.value = value;
        }

        static ErrorStrategy parse(String value)
        {
            for (ErrorStrategy strategy : values())
            {
                if (strategy.value.equalsIgnoreCase(value))
                    return strategy;
            }
            throw new IllegalArgumentException("Unbekannte sync.beiFehler-Strategie: " + value
                + ". Erlaubt sind stoppen, fortsetzen, nachfragen.");
        }
    }

    record SyncOptions(ErrorStrategy strategy, boolean explicit)
    {
    }

    private record SyncTarget(String name, Callable<List<Synchronization>> syncFactory)
    {
    }

    private record SyncError(String account, String message)
    {
        Map<String, Object> toMap()
        {
            return Map.of("konto", account, "meldung", message);
        }
    }

    private static final class SyncSummary
    {
        private final String accounts;
        private final boolean testRun;
        private boolean started;
        private int jobs;
        private int successful;
        private int failed;
        private boolean aborted;
        private final List<SyncError> errors = new ArrayList<>();

        private SyncSummary(String accounts, boolean testRun)
        {
            this.accounts = accounts;
            this.testRun = testRun;
        }

        private Map<String, Object> toMap(String status)
        {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("gestartet", started);
            result.put("testlauf", testRun);
            result.put("konten", accounts);
            result.put("jobs", jobs);
            result.put("status", status);
            result.put("erfolgreich", successful);
            result.put("fehlgeschlagen", failed);
            result.put("abgebrochen", aborted);
            result.put("fehler", errors.stream().map(SyncError::toMap).toList());
            return result;
        }
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
