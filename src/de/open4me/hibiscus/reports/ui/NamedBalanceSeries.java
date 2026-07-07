package de.open4me.hibiscus.reports.ui;

import java.rmi.RemoteException;
import java.util.List;

import de.willuhn.jameica.hbci.gui.chart.AbstractChartDataSaldo;
import de.willuhn.jameica.hbci.server.Value;

final class NamedBalanceSeries extends AbstractChartDataSaldo
{
    private final String label;
    private final List<Value> data;
    private final int lineWidth;
    private final BalanceSeriesDetails details;

    NamedBalanceSeries(String label, List<Value> data, int lineWidth,
                       List<BalanceSeriesDetails.AccountSeries> accounts)
    {
        this.label = label;
        this.data = List.copyOf(data);
        this.lineWidth = lineWidth;
        this.details = new BalanceSeriesDetails(accounts);
    }

    @Override
    public List<Value> getData()
    {
        return data;
    }

    @Override
    public String getLabel()
    {
        return label;
    }

    @Override
    public int getLineWidth() throws RemoteException
    {
        return lineWidth;
    }

    @Override
    public boolean isFilled()
    {
        return false;
    }

    List<BalanceSeriesDetails.AccountValue> detailsAt(long time)
    {
        return details.nonZeroAt(time);
    }
}
