package de.open4me.hibiscus.reports.mcp;

import java.util.List;
import java.util.Map;

import de.open4me.hibiscus.reports.model.ReportAccount;

interface McpAccountSynchronizer
{
    Map<String, Object> sync(List<ReportAccount> accounts, boolean all) throws Exception;
}
