package de.open4me.hibiscus.reports.mcp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.open4me.hibiscus.reports.automation.runtime.AutomationContext;
import de.open4me.hibiscus.reports.automation.runtime.AutomationDialogGate;
import de.open4me.hibiscus.reports.automation.runtime.AutomationSync;
import de.open4me.hibiscus.reports.automation.runtime.SyncLog;
import de.open4me.hibiscus.reports.model.ReportAccount;
import de.willuhn.logging.Logger;

final class HibiscusMcpAccountSynchronizer implements McpAccountSynchronizer
{
    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> sync(List<ReportAccount> accounts, boolean all) throws Exception
    {
        List<String> logs = new ArrayList<>();
        SyncLog log = new SyncLog()
        {
            @Override
            public void info(String message)
            {
                logs.add("info: " + message);
                Logger.info("MCP Sync: " + message);
            }

            @Override
            public void warn(String message)
            {
                logs.add("warn: " + message);
                Logger.warn("MCP Sync: " + message);
            }

            @Override
            public void error(String message)
            {
                logs.add("error: " + message);
                Logger.error("MCP Sync: " + message);
            }
        };

        AutomationDialogGate gate = new AutomationDialogGate();
        gate.start();
        try
        {
            AutomationContext context = new AutomationContext("mcp", "MCP Sync",
                "mcp-" + System.currentTimeMillis(), null, "mcp", false, true);
            AutomationSync sync = new AutomationSync(context, log, gate, () -> {
            });
            Object raw = all ? sync.alle() : sync.starten(accounts);
            Map<String, Object> result = raw instanceof Map<?, ?> map
                ? new LinkedHashMap<>((Map<String, Object>) map)
                : new LinkedHashMap<>();
            result.put("logs", logs);
            result.put("finishedAt", LocalDateTime.now().toString());
            return result;
        }
        finally
        {
            gate.stop();
        }
    }
}
