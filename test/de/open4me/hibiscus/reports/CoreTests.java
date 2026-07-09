package de.open4me.hibiscus.reports;

import de.open4me.hibiscus.reports.ui.SankeyExportTests;
import de.open4me.hibiscus.reports.ui.OverviewExportTests;
import de.open4me.hibiscus.reports.ui.AccountSelectionSettingsTests;
import de.open4me.hibiscus.reports.ui.GroupExclusionSettingsTests;
import de.open4me.hibiscus.reports.ui.BalanceSeriesDetailsTests;
import de.open4me.hibiscus.reports.ui.AccountBalanceConsistencyTests;
import de.open4me.hibiscus.reports.ui.DepotBalanceValidationTests;
import de.open4me.hibiscus.reports.ui.ReportsNavigationExtensionTests;
import de.open4me.hibiscus.reports.data.HibiscusDataProviderTests;
import de.open4me.hibiscus.reports.data.BalanceSeriesAggregatorTests;
import de.open4me.hibiscus.reports.data.DynamicReportTests;

public final class CoreTests
{
    private CoreTests()
    {
    }

    public static void main(String[] args)
    {
        FlowAggregatorTests.run();
        PeriodBalanceAggregatorTests.run();
        HibiscusDataProviderTests.run();
        BalanceSeriesAggregatorTests.run();
        DynamicReportTests.run();
        SankeyGraphBuilderTests.run();
        SankeyExportTests.run();
        OverviewExportTests.run();
        AccountSelectionSettingsTests.run();
        GroupExclusionSettingsTests.run();
        BalanceSeriesDetailsTests.run();
        AccountBalanceConsistencyTests.run();
        DepotBalanceValidationTests.run();
        ReportsNavigationExtensionTests.run();
        HelpResourceTests.run();
        System.out.println("All hibiscus.ly.reports tests passed");
    }
}
