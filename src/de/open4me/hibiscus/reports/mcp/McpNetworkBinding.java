package de.open4me.hibiscus.reports.mcp;

final class McpNetworkBinding
{
    private McpNetworkBinding()
    {
    }

    static String bindAddress(boolean localNetworkEnabled)
    {
        return localNetworkEnabled ? "0.0.0.0" : "127.0.0.1";
    }

    static String endpoint(int port, boolean localNetworkEnabled)
    {
        return "http://" + bindAddress(localNetworkEnabled) + ":" + port + "/mcp";
    }
}
