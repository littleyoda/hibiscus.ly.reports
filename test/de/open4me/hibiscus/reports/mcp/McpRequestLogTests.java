package de.open4me.hibiscus.reports.mcp;

public final class McpRequestLogTests
{
    private McpRequestLogTests()
    {
    }

    public static void run()
    {
        boundedSessionHistory();
        metadataWithoutContents();
    }

    private static void boundedSessionHistory()
    {
        McpRequestLog log = new McpRequestLog(3);
        for (int i = 0; i < 4; i++)
            log.add("Lokal", "method-" + i, 200, "", i);
        check(log.snapshot().size() == 3, "history is bounded");
        check("method-1".equals(log.snapshot().get(0).operation()), "oldest entry is removed");
        check(log.version() == 4, "version tracks additions");
    }

    private static void metadataWithoutContents()
    {
        String request = "{\"jsonrpc\":\"2.0\",\"method\":\"tools/call\",\"params\":"
            + "{\"name\":\"hibiscus_accounts_list\",\"secret\":\"private-account-data\"}}";
        String operation = McpRequestLog.operation(request);
        check("tools/call: hibiscus_accounts_list".equals(operation), "tool name is logged");
        check(!operation.contains("private-account-data"), "arguments are not logged");
        check(McpRequestLog.operation("not-json").equals("Ungültiges JSON"), "invalid JSON is classified");
        check(McpRequestLog.operation("[{\"method\":\"initialize\"},{\"method\":\"tools/list\"}]")
            .equals("Batch: initialize, tools/list"), "batch methods are summarized");
        check(McpRequestLog.rpcError("{\"error\":{\"code\":-32603,\"message\":\"private-data\"}}")
            .equals("RPC -32603"), "only JSON-RPC error code is logged");
    }

    private static void check(boolean condition, String message)
    {
        if (!condition)
            throw new AssertionError(message);
    }
}
