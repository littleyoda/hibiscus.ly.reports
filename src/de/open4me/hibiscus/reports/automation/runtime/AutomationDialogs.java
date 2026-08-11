package de.open4me.hibiscus.reports.automation.runtime;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Text;

import de.open4me.hibiscus.reports.data.ReportAccountsProxy;
import de.open4me.hibiscus.reports.model.ReportAccount;
import de.willuhn.jameica.gui.Action;
import de.willuhn.jameica.gui.GUI;
import de.willuhn.jameica.gui.dialogs.AbstractDialog;
import de.willuhn.jameica.gui.dialogs.SimpleDialog;
import de.willuhn.jameica.gui.dialogs.TextDialog;
import de.willuhn.jameica.gui.input.SelectInput;
import de.willuhn.jameica.gui.parts.ButtonArea;
import de.willuhn.jameica.gui.util.Container;
import de.willuhn.jameica.gui.util.SimpleContainer;
import de.willuhn.jameica.messaging.StatusBarMessage;
import de.willuhn.jameica.system.Application;
import de.willuhn.jameica.system.OperationCanceledException;
import de.willuhn.util.ApplicationException;

public final class AutomationDialogs
{
    private static final String HBCI_BACKEND = "de.willuhn.jameica.hbci.synchronize.hbci.HBCISynchronizeBackend";

    private final AutomationLogger log;
    private final ReportAccountsProxy accounts;
    private final AutomationDialogGate dialogGate;
    private final Runnable waitingStarted;
    private final Runnable waitingFinished;

    public AutomationDialogs(AutomationLogger log, ReportAccountsProxy accounts, AutomationDialogGate dialogGate)
    {
        this(log, accounts, dialogGate, null, null);
    }

    public AutomationDialogs(AutomationLogger log, ReportAccountsProxy accounts, AutomationDialogGate dialogGate,
                             Runnable waitingStarted, Runnable waitingFinished)
    {
        this.log = log;
        this.accounts = accounts;
        this.dialogGate = dialogGate;
        this.waitingStarted = waitingStarted;
        this.waitingFinished = waitingFinished;
    }

    public boolean bestaetigen(String titel, String text)
    {
        awaitDialogAllowed();
        AtomicReference<Boolean> result = new AtomicReference<>(false);
        GUI.getDisplay().syncExec(() -> {
            try
            {
                result.set(de.willuhn.jameica.system.Application.getCallback().askUser(text));
            }
            catch (Exception e)
            {
                if (isCanceled(e))
                {
                    result.set(false);
                    return;
                }
                throw new IllegalStateException(e);
            }
        });
        log.info("Bestaetigungsdialog '" + safe(titel) + "': " + result.get());
        return result.get();
    }

    public void info(String titel, String text)
    {
        awaitDialogAllowed();
        GUI.getDisplay().syncExec(() -> {
            try
            {
                SimpleDialog dialog = new SimpleDialog(AbstractDialog.POSITION_CENTER);
                dialog.setTitle(titel == null || titel.isBlank() ? "Information" : titel);
                dialog.setText(text == null ? "" : text);
                dialog.open();
            }
            catch (Exception e)
            {
                throw new IllegalStateException(e);
            }
        });
        log.info("Info-Dialog '" + safe(titel) + "' angezeigt.");
    }

    public void hinweis(String titel, String text)
    {
        info(titel, text);
    }

    public void text(String titel, String text)
    {
        awaitDialogAllowed();
        GUI.getDisplay().syncExec(() -> {
            try
            {
                new LongTextDialog(titel, text).open();
            }
            catch (Exception e)
            {
                throw new IllegalStateException(e);
            }
        });
        log.info("Text-Dialog '" + safe(titel) + "' angezeigt.");
    }

    public DialogResult<String> eingabe(String titel, String text)
    {
        awaitDialogAllowed();
        AtomicReference<DialogResult<String>> result = new AtomicReference<>(DialogResult.abgebrochen());
        GUI.getDisplay().syncExec(() -> {
            try
            {
                TextDialog dialog = new TextDialog(AbstractDialog.POSITION_CENTER);
                dialog.setTitle(titel == null ? "Eingabe" : titel);
                dialog.setText(text == null ? "" : text);
                Object opened = dialog.open();
                String value = opened == null ? null : opened.toString();
                result.set(value == null ? DialogResult.abgebrochen() : DialogResult.ok(value));
            }
            catch (Exception e)
            {
                if (isCanceled(e))
                {
                    result.set(DialogResult.abgebrochen());
                    return;
                }
                throw new IllegalStateException(e);
            }
        });
        return result.get();
    }

    public DialogResult<ReportAccount> kontoAuswaehlen(String titel)
    {
        awaitDialogAllowed();
        AtomicReference<DialogResult<ReportAccount>> result = new AtomicReference<>(DialogResult.abgebrochen());
        GUI.getDisplay().syncExec(() -> {
            try
            {
                result.set(new AccountChoiceDialog(titel, selectableAccounts()).open());
            }
            catch (Exception e)
            {
                throw new IllegalStateException(e);
            }
        });
        return result.get();
    }

    private List<ReportAccount> selectableAccounts()
    {
        return accounts.getAktive().stream()
            .filter(ReportAccount::getAktiv)
            .filter(account -> !account.getOffline())
            .filter(account -> !account.getDepot())
            .filter(account -> HBCI_BACKEND.equals(account.getBackendClass()))
            .toList();
    }

    private void awaitDialogAllowed()
    {
        dialogGate.awaitDialogAllowed(waitingStarted, waitingFinished);
    }

    private static String safe(String value)
    {
        return value == null ? "" : value;
    }

    private static boolean isCanceled(Throwable error)
    {
        Throwable current = error;
        while (current != null)
        {
            if (current instanceof OperationCanceledException)
                return true;
            current = current.getCause();
        }
        return false;
    }

    private static final class AccountChoiceDialog extends AbstractDialog<DialogResult<ReportAccount>>
    {
        private final String title;
        private final List<ReportAccount> accounts;
        private SelectInput input;
        private DialogResult<ReportAccount> result = DialogResult.abgebrochen();

        AccountChoiceDialog(String title, List<ReportAccount> accounts)
        {
            super(POSITION_CENTER);
            this.title = title == null || title.isBlank() ? "Konto auswaehlen" : title;
            this.accounts = accounts;
            setTitle(this.title);
        }

        @Override
        protected void paint(org.eclipse.swt.widgets.Composite parent) throws Exception
        {
            Container container = new SimpleContainer(parent);
            input = new SelectInput(accounts, accounts.isEmpty() ? null : accounts.get(0));
            input.setName("Konto");
            container.addInput(input);
            ButtonArea buttons = new ButtonArea();
            buttons.addButton("OK", context -> {
                Object value = input.getValue();
                if (value instanceof ReportAccount account)
                    result = DialogResult.ok(account);
                close();
            }, null, true, "ok.png");
            buttons.addButton("Abbrechen", context -> {
                result = DialogResult.abgebrochen();
                close();
            }, null, false, "process-stop.png");
            container.addButtonArea(buttons);
        }

        @Override
        protected DialogResult<ReportAccount> getData()
        {
            return result;
        }
    }

    private static final class LongTextDialog extends AbstractDialog<Void>
    {
        private static final int WINDOW_WIDTH = 900;
        private static final int WINDOW_HEIGHT = 650;

        private final String value;

        LongTextDialog(String title, String value)
        {
            super(POSITION_CENTER);
            setTitle(title == null || title.isBlank() ? "Text" : title);
            setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
            this.value = value == null ? "" : value;
        }

        @Override
        protected void paint(org.eclipse.swt.widgets.Composite parent) throws Exception
        {
            parent.setLayout(new GridLayout(1, false));

            Text text = new Text(parent, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL | SWT.H_SCROLL
                | SWT.READ_ONLY);
            text.setText(value);
            text.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

            ButtonArea buttons = new ButtonArea();
            buttons.addButton("Kopieren", new Action()
            {
                @Override
                public void handleAction(Object context) throws ApplicationException
                {
                    copyToClipboard(value);
                }
            }, null, false, "edit-copy.png");
            buttons.addButton("Schließen", context -> close(), null, true, "ok.png");
            buttons.paint(parent);

            getShell().setMinimumSize(getShell().computeSize(WINDOW_WIDTH, WINDOW_HEIGHT));
        }

        private static void copyToClipboard(String value) throws ApplicationException
        {
            try
            {
                Clipboard clipboard = new Clipboard(GUI.getDisplay());
                try
                {
                    clipboard.setContents(new Object[] { value }, new Transfer[] { TextTransfer.getInstance() });
                }
                finally
                {
                    clipboard.dispose();
                }
                Application.getMessagingFactory().sendMessage(new StatusBarMessage(
                    "Text wurde in die Zwischenablage kopiert.", StatusBarMessage.TYPE_SUCCESS));
            }
            catch (Exception e)
            {
                throw new ApplicationException("Text konnte nicht kopiert werden: " + e.getMessage(), e);
            }
        }

        @Override
        protected Void getData()
        {
            return null;
        }
    }
}
