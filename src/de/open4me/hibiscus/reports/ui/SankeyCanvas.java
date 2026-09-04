package de.open4me.hibiscus.reports.ui;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseTrackAdapter;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Transform;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;

import de.open4me.hibiscus.reports.model.SankeyGraph;

public class SankeyCanvas extends Canvas
{
    private SankeyGraph graph;
    private SankeyLayout.Scene scene;
    private int zoomPercent = 100;
    private SankeyText.DetailOptions detailOptions = SankeyText.DetailOptions.DEFAULT;
    private Consumer<String> categoryToggle = ignored -> { };
    private Consumer<SankeyGraph.Node> transactionOpen = ignored -> { };
    private Consumer<SankeyGraph.Node> areaHide = ignored -> { };
    private Runnable hideUndo = () -> { };
    private Runnable showAll = () -> { };
    private BooleanSupplier hasHiddenAreas = () -> false;

    public SankeyCanvas(Composite parent, int style)
    {
        super(parent, style | SWT.DOUBLE_BUFFERED);
        addPaintListener(event -> {
            float scale = zoomPercent / 100f;
            int logicalHeight = Math.max(1, Math.round(getClientArea().height / scale));
            scene = SankeyLayout.create(graph, logicalHeight);
            Transform transform = new Transform(getDisplay());
            try
            {
                transform.scale(scale, scale);
                event.gc.setTransform(transform);
                SankeySwtRenderer.paint(event.gc, getDisplay(), graph, scene, 0, true, false, detailOptions);
            }
            finally
            {
                event.gc.setTransform(null);
                transform.dispose();
            }
        });
        addMouseMoveListener(this::mouseMoved);
        addMouseListener(new org.eclipse.swt.events.MouseAdapter()
        {
            @Override
            public void mouseUp(MouseEvent event)
            {
                SankeyLayout.NodePlacement placement = find(event.x, event.y);
                if (event.button == 3)
                {
                    showContextMenu(event, placement);
                    return;
                }
                if (placement != null && placement.node().expandableKey() != null)
                    categoryToggle.accept(placement.node().expandableKey());
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

    public void setGraph(SankeyGraph graph)
    {
        this.graph = graph;
        this.scene = null;
        redraw();
    }

    public void setDetailOptions(SankeyText.DetailOptions detailOptions)
    {
        this.detailOptions = detailOptions == null ? SankeyText.DetailOptions.DEFAULT : detailOptions;
        redraw();
    }

    public void setCategoryToggle(Consumer<String> categoryToggle)
    {
        this.categoryToggle = categoryToggle == null ? ignored -> { } : categoryToggle;
    }

    public void setTransactionOpen(Consumer<SankeyGraph.Node> transactionOpen)
    {
        this.transactionOpen = transactionOpen == null ? ignored -> { } : transactionOpen;
    }

    public void setVisibilityActions(Consumer<SankeyGraph.Node> areaHide, Runnable hideUndo,
                                     Runnable showAll, BooleanSupplier hasHiddenAreas)
    {
        this.areaHide = areaHide == null ? ignored -> { } : areaHide;
        this.hideUndo = hideUndo == null ? () -> { } : hideUndo;
        this.showAll = showAll == null ? () -> { } : showAll;
        this.hasHiddenAreas = hasHiddenAreas == null ? () -> false : hasHiddenAreas;
    }

    public Point preferredSize()
    {
        double scale = zoomPercent / 100d;
        return new Point((int) Math.round(SankeyLayout.preferredWidth(graph) * scale),
            (int) Math.round(SankeyLayout.preferredHeight(graph) * scale));
    }

    public int getZoomPercent()
    {
        return zoomPercent;
    }

    public void setZoomPercent(int zoomPercent)
    {
        this.zoomPercent = Math.max(50, Math.min(200, zoomPercent));
        this.scene = null;
        redraw();
    }

    private void mouseMoved(MouseEvent event)
    {
        SankeyLayout.NodePlacement placement = find(event.x, event.y);
        if (placement == null)
        {
            setToolTipText(null);
            setCursor(null);
            return;
        }
        setToolTipText(placement.node().name() + "\n"
            + SankeyText.detailLine(graph, placement.node(), detailOptions));
        setCursor(placement.node().expandableKey() == null
            && !canOpenTransactions(placement.node())
            ? null : getDisplay().getSystemCursor(SWT.CURSOR_HAND));
    }

    private void showContextMenu(MouseEvent event, SankeyLayout.NodePlacement placement)
    {
        SankeyGraph.Node node = placement == null ? null : placement.node();
        boolean transactions = canOpenTransactions(node);
        boolean hideable = node != null && !node.exclusionKeys().isEmpty();
        boolean restore = hasHiddenAreas.getAsBoolean();
        if (!transactions && !hideable && !restore)
            return;

        Menu menu = new Menu(this);
        if (transactions)
        {
            MenuItem transactionsItem = new MenuItem(menu, SWT.PUSH);
            transactionsItem.setText("Umsätze anzeigen...");
            transactionsItem.addListener(SWT.Selection, ignored -> transactionOpen.accept(node));
        }
        if (transactions && hideable)
            new MenuItem(menu, SWT.SEPARATOR);
        if (hideable)
        {
            MenuItem hideItem = new MenuItem(menu, SWT.PUSH);
            hideItem.setText("Ausblenden");
            hideItem.addListener(SWT.Selection, ignored -> areaHide.accept(node));
        }
        if ((transactions || hideable) && restore)
            new MenuItem(menu, SWT.SEPARATOR);
        if (restore)
        {
            MenuItem undoItem = new MenuItem(menu, SWT.PUSH);
            undoItem.setText("Ausblenden rückgängig");
            undoItem.addListener(SWT.Selection, ignored -> hideUndo.run());
            MenuItem showAllItem = new MenuItem(menu, SWT.PUSH);
            showAllItem.setText("Alles einblenden");
            showAllItem.addListener(SWT.Selection, ignored -> showAll.run());
        }
        menu.addListener(SWT.Hide, ignored -> getDisplay().asyncExec(() -> {
            if (!menu.isDisposed())
                menu.dispose();
        }));
        Point location = toDisplay(event.x, event.y);
        menu.setLocation(location);
        menu.setVisible(true);
    }

    private static boolean canOpenTransactions(SankeyGraph.Node node)
    {
        return node != null && node.transactionFilter() != null && node.transactionFilter().canOpen();
    }

    private SankeyLayout.NodePlacement find(int x, int y)
    {
        double scale = zoomPercent / 100d;
        int logicalX = (int) Math.floor(x / scale);
        int logicalY = (int) Math.floor(y / scale);
        if (scene == null)
            scene = SankeyLayout.create(graph, Math.max(1, (int) Math.round(getClientArea().height / scale)));
        return SankeyLayout.find(scene, logicalX, logicalY);
    }
}
