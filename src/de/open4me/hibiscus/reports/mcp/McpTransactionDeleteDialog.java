package de.open4me.hibiscus.reports.mcp;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Text;

import de.willuhn.jameica.gui.Action;
import de.willuhn.jameica.gui.GUI;
import de.willuhn.jameica.gui.dialogs.AbstractDialog;
import de.willuhn.jameica.gui.parts.ButtonArea;
import de.willuhn.jameica.gui.util.Container;
import de.willuhn.jameica.gui.util.SimpleContainer;
import de.willuhn.jameica.system.Application;
import de.willuhn.jameica.system.OperationCanceledException;

/**
 * Asks the user in Hibiscus whether the transactions listed by an MCP client may be deleted. Only the
 * explicit "Löschen" button confirms; cancel, window close, Escape (Jameica's default onEscape throws
 * OperationCanceledException) and timeout all answer no.
 */
final class McpTransactionDeleteDialog extends AbstractDialog<Boolean>
{
    private static final int WIDTH = 700;
    private static final int HEIGHT = 400;

    /** Only one confirmation may be open at a time; concurrent MCP calls must not nest modal dialogs. */
    private static final AtomicBoolean OPEN = new AtomicBoolean();

    private final String text;
    private boolean confirmed;

    private McpTransactionDeleteDialog(String text)
    {
        super(POSITION_CENTER);
        this.text = text;
        setTitle("Umsätze löschen (MCP)");
        setSize(WIDTH, HEIGHT);
    }

    /**
     * Shows the dialog and blocks until it is answered or the timeout expires.
     *
     * @return true only if the user pressed "Löschen"
     */
    static boolean confirm(String text, Supplier<Duration> remainingTime) throws Exception
    {
        if (Application.inServerMode())
            throw new IllegalStateException("Hibiscus läuft ohne Oberfläche; Löschen kann nicht bestätigt werden.");

        if (!OPEN.compareAndSet(false, true))
            throw new IllegalStateException("Es wartet bereits eine Löschbestätigung in Hibiscus.");
        try
        {
            return show(text, remainingTime);
        }
        finally
        {
            OPEN.set(false);
        }
    }

    private static boolean show(String text, Supplier<Duration> remainingTime) throws Exception
    {
        Display display = GUI.getDisplay();
        AtomicReference<Boolean> answer = new AtomicReference<>(Boolean.FALSE);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        // AbstractDialog.open() runs its nested event loop on the UI thread and blocks the caller until the
        // dialog is closed. We enter the UI thread ourselves so that timerExec is registered there too.
        display.syncExec(() -> {
            // Measured here, after any wait for the UI thread, so the dialog closes before the client gives up.
            Duration timeout = remainingTime.get();
            if (timeout.compareTo(HibiscusTransactionDeleter.MIN_CONFIRM_TIME) < 0)
            {
                failure.set(new IllegalStateException(
                    "Zu wenig Zeit für die Bestätigung in Hibiscus; bitte erneut aufrufen."));
                return;
            }
            McpTransactionDeleteDialog dialog = new McpTransactionDeleteDialog(text);
            Runnable timeoutTask = dialog::close;
            display.timerExec((int) Math.min(Integer.MAX_VALUE, timeout.toMillis()), timeoutTask);
            try
            {
                answer.set(Boolean.TRUE.equals(dialog.open()));
            }
            catch (OperationCanceledException e)
            {
                answer.set(Boolean.FALSE);
            }
            catch (Throwable t)
            {
                failure.set(t);
            }
            finally
            {
                display.timerExec(-1, timeoutTask);
            }
        });
        Throwable error = failure.get();
        if (error instanceof Exception exception)
            throw exception;
        if (error instanceof Error unchecked)
            throw unchecked;
        return answer.get();
    }

    @Override
    protected void paint(Composite parent) throws Exception
    {
        Container container = new SimpleContainer(parent, true);
        Text body = new Text(container.getComposite(),
            SWT.MULTI | SWT.READ_ONLY | SWT.WRAP | SWT.V_SCROLL | SWT.BORDER);
        body.setText(text);
        GridData bodyData = new GridData(SWT.FILL, SWT.FILL, true, true);
        bodyData.horizontalSpan = 2;
        body.setLayoutData(bodyData);

        ButtonArea buttons = new ButtonArea();
        buttons.addButton("Löschen", new Action()
        {
            @Override
            public void handleAction(Object context)
            {
                confirmed = true;
                close();
            }
        }, null, false, "user-trash-full.png");
        buttons.addButton("Abbrechen", new Action()
        {
            @Override
            public void handleAction(Object context)
            {
                close();
            }
        }, null, true, "process-stop.png");
        container.addButtonArea(buttons);
    }

    @Override
    protected Boolean getData()
    {
        return confirmed;
    }
}
