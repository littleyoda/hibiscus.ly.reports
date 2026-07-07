package de.open4me.hibiscus.reports.ui;

import java.rmi.RemoteException;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swtchart.IAxis;

import de.willuhn.jameica.hbci.gui.chart.ChartFeature;
import de.willuhn.jameica.hbci.gui.chart.ChartFeatureTooltip;
import de.willuhn.jameica.hbci.gui.chart.LineChart;

final class GroupBalanceLineChart extends LineChart
{
    @Override
    public void paint(Composite parent) throws RemoteException
    {
        super.paint(parent);
        applyThemeColors();
    }

    @Override
    public void redraw() throws RemoteException
    {
        super.redraw();
        applyThemeColors();
    }

    @Override
    public void addFeature(ChartFeature feature)
    {
        if (feature instanceof ChartFeatureTooltip)
            return;
        super.addFeature(feature);
    }

    private void applyThemeColors()
    {
        org.eclipse.swtchart.Chart chart = getChart();
        if (chart == null || chart.isDisposed())
            return;

        Display display = chart.getDisplay();
        Color background = display.getSystemColor(SWT.COLOR_WIDGET_BACKGROUND);
        Color foreground = display.getSystemColor(SWT.COLOR_WIDGET_FOREGROUND);
        Color grid = display.getSystemColor(SWT.COLOR_WIDGET_NORMAL_SHADOW);

        chart.setBackground(background);
        chart.setBackgroundInPlotArea(background);
        chart.getPlotArea().getControl().setBackground(background);
        chart.getTitle().setForeground(foreground);
        chart.getLegend().setBackground(background);
        chart.getLegend().setForeground(foreground);

        IAxis xAxis = chart.getAxisSet().getXAxis(0);
        xAxis.getTitle().setForeground(background);
        xAxis.getTick().setForeground(foreground);
        xAxis.getGrid().setForeground(grid);

        IAxis yAxis = chart.getAxisSet().getYAxis(0);
        yAxis.getTick().setForeground(foreground);
        yAxis.getGrid().setForeground(grid);
    }
}
