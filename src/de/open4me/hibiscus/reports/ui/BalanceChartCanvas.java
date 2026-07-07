package de.open4me.hibiscus.reports.ui;

import java.time.format.DateTimeFormatter;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseTrackAdapter;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;

import de.open4me.hibiscus.reports.model.PeriodBalance;

final class BalanceChartCanvas extends Canvas
{
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private List<PeriodBalance> periods = List.of();
    private BiConsumer<PeriodBalance, FlowTransactionSelection.Sign> transactionOpen = (period, sign) -> { };

    BalanceChartCanvas(Composite parent, int style)
    {
        super(parent, style | SWT.DOUBLE_BUFFERED);
        addPaintListener(event -> BalanceChartSwtRenderer.paint(event.gc, getDisplay(), periods,
            getClientArea().width, 0));
        addMouseMoveListener(this::mouseMoved);
        addMouseListener(new org.eclipse.swt.events.MouseAdapter()
        {
            @Override
            public void mouseUp(MouseEvent event)
            {
                if (event.button != 3)
                    return;
                showContextMenu(event, find(event));
            }
        });
        addMouseTrackListener(new MouseTrackAdapter()
        {
            @Override
            public void mouseExit(MouseEvent event)
            {
                setToolTipText(null);
                setCursor(null);
            }
        });
    }

    void setPeriods(List<PeriodBalance> periods)
    {
        this.periods = periods == null ? List.of() : List.copyOf(periods);
        redraw();
    }

    void setTransactionOpen(BiConsumer<PeriodBalance, FlowTransactionSelection.Sign> transactionOpen)
    {
        this.transactionOpen = transactionOpen == null ? (period, sign) -> { } : transactionOpen;
    }

    Point preferredSize()
    {
        return new Point(BalanceChartGeometry.preferredWidth(periods), BalanceChartGeometry.HEIGHT);
    }

    private void mouseMoved(MouseEvent event)
    {
        BalanceChartGeometry.Item item = find(event);
        if (item == null)
        {
            setToolTipText(null);
            setCursor(null);
            return;
        }
        setCursor(getDisplay().getSystemCursor(SWT.CURSOR_HAND));
        PeriodBalance value = item.value();
        NumberFormat euro = NumberFormat.getIntegerInstance(Locale.GERMANY);
        setToolTipText(value.label() + " (" + DATE.format(value.start()) + " – " + DATE.format(value.end()) + ")"
            + "\nEinnahmen: " + euro.format(value.income()) + " €"
            + "\nAusgaben: " + euro.format(value.expenses()) + " €"
            + "\nBilanz: " + euro.format(value.balance()) + " €");
    }

    private BalanceChartGeometry.Item find(MouseEvent event)
    {
        BalanceChartGeometry.Item item = BalanceChartGeometry.create(periods, getClientArea().width).at(event.x);
        if (item == null || event.y < BalanceChartGeometry.plotTop()
            || event.y > BalanceChartGeometry.plotBottom())
            return null;
        return item;
    }

    private void showContextMenu(MouseEvent event, BalanceChartGeometry.Item item)
    {
        if (item == null)
            return;

        PeriodBalance period = item.value();
        Menu menu = new Menu(this);
        addMenuItem(menu, "Alle Umsätze anzeigen...", period, FlowTransactionSelection.Sign.ALL);
        addMenuItem(menu, "Einnahmen anzeigen...", period, FlowTransactionSelection.Sign.INCOME);
        addMenuItem(menu, "Ausgaben anzeigen...", period, FlowTransactionSelection.Sign.EXPENSE);
        menu.addListener(SWT.Hide, ignored -> getDisplay().asyncExec(() -> {
            if (!menu.isDisposed())
                menu.dispose();
        }));
        Point location = toDisplay(event.x, event.y);
        menu.setLocation(location);
        menu.setVisible(true);
    }

    private void addMenuItem(Menu menu, String label, PeriodBalance period,
                             FlowTransactionSelection.Sign sign)
    {
        MenuItem item = new MenuItem(menu, SWT.PUSH);
        item.setText(label);
        item.addListener(SWT.Selection, ignored -> transactionOpen.accept(period, sign));
    }
}
