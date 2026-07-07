package de.open4me.hibiscus.reports.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import de.willuhn.jameica.hbci.server.Value;

final class BalanceSeriesDetails
{
    private static final double HALF_CENT = 0.005d;

    record AccountSeries(String accountId, String accountName, List<Value> values)
    {
        AccountSeries
        {
            if (accountId == null || accountId.isBlank())
                throw new IllegalArgumentException("accountId must not be empty");
            values = values == null ? List.of() : List.copyOf(values);
        }
    }

    record AccountValue(String accountId, String accountName, double value)
    {
    }

    private record AccountTimeline(String accountName, NavigableMap<Long, Double> values)
    {
    }

    private final Map<String, AccountTimeline> accountsById;

    BalanceSeriesDetails(List<AccountSeries> accounts)
    {
        Map<String, AccountTimeline> indexed = new LinkedHashMap<>();
        for (AccountSeries account : accounts)
        {
            if (account == null)
                continue;
            NavigableMap<Long, Double> timeline = new TreeMap<>();
            for (Value value : account.values())
            {
                if (value == null || value.getDate() == null)
                    continue;
                timeline.put(value.getDate().getTime(), value.getValue());
            }
            indexed.put(account.accountId(), new AccountTimeline(account.accountName(), timeline));
        }
        accountsById = Collections.unmodifiableMap(new LinkedHashMap<>(indexed));
    }

    List<AccountValue> at(long time)
    {
        List<AccountValue> result = new ArrayList<>();
        for (Map.Entry<String, AccountTimeline> account : accountsById.entrySet())
        {
            Map.Entry<Long, Double> value = account.getValue().values().floorEntry(time);
            if (value != null)
                result.add(new AccountValue(account.getKey(), account.getValue().accountName(), value.getValue()));
        }
        return List.copyOf(result);
    }

    List<AccountValue> nonZeroAt(long time)
    {
        return at(time).stream().filter(value -> Math.abs(value.value()) >= HALF_CENT).toList();
    }
}
