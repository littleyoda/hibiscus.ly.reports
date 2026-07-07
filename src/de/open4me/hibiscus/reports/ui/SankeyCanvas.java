package de.open4me.hibiscus.reports.ui;

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
    private Consumer<String> categoryToggle = ignored -> { };
    private Consumer<SankeyGraph.Node> transactionOpen = ignored -> { };

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
                SankeySwtRenderer.paint(event.gc, getDisplay(), graph, scene, 0, true, false);
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

    public void setCategoryToggle(Consumer<String> categoryToggle)
    {
        this.categoryToggle = categoryToggle == null ? ignored -> { } : categoryToggle;
    }

    public void setTransactionOpen(Consumer<SankeyGraph.Node> transactionOpen)
    {
        this.transactionOpen = transactionOpen == null ? ignored -> { } : transactionOpen;
    }

    public Point preferredSize()
    {
        double scale = zoomPercent / 100d;
        return new Point((int) Math.round(SankeyLayout.WIDTH * scale),
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
        setToolTipText(placement.node().name() + "\n" + SankeyText.detailLine(graph, placement.node()));
        setCursor(placement.node().expandableKey() == null
            && !canOpenTransactions(placement.node())
            ? null : getDisplay().getSystemCursor(SWT.CURSOR_HAND));
    }

    private void showContextMenu(MouseEvent event, SankeyLayout.NodePlacement placement)
    {
        if (placement == null || !canOpenTransactions(placement.node()))
            return;

        SankeyGraph.Node node = placement.node();
        Menu menu = new Menu(this);
        MenuItem item = new MenuItem(menu, SWT.PUSH);
        item.setText("Umsätze anzeigen...");
        item.addListener(SWT.Selection, ignored -> transactionOpen.accept(node));
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
        return scene.nodes().stream().filter(node -> node.bounds().contains(logicalX, logicalY))
            .findFirst().orElse(null);
    }
}
