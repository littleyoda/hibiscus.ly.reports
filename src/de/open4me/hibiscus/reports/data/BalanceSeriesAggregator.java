package de.open4me.hibiscus.reports.data;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import de.willuhn.jameica.hbci.server.Value;

/** Adds daily balance series without relying on identical list lengths. */
public final class BalanceSeriesAggregator
{
    public List<Value> sum(List<? extends List<Value>> series)
    {
        Map<Long, Double> values = new TreeMap<>();
        for (List<Value> points : series)
        {
            if (points == null)
                continue;
            for (Value point : points)
            {
                if (point == null || point.getDate() == null)
                    continue;
                values.merge(point.getDate().getTime(), point.getValue(), Double::sum);
            }
        }

        List<Value> result = new ArrayList<>(values.size());
        for (Map.Entry<Long, Double> entry : values.entrySet())
            result.add(new Value(new Date(entry.getKey()), entry.getValue()));
        return result;
    }
}
