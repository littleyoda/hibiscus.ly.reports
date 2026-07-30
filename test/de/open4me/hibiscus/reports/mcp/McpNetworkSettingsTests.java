package de.open4me.hibiscus.reports.mcp;

import java.util.List;
import java.util.Objects;

public final class McpNetworkSettingsTests
{
    private McpNetworkSettingsTests()
    {
    }

    public static void run()
    {
        usesLocalhostByDefault();
        usesAllInterfacesForLocalNetwork();
        validatesOriginPolicy();
    }

    private static void usesLocalhostByDefault()
    {
        checkEquals("127.0.0.1", McpNetworkBinding.bindAddress(false), "default bind address");
        checkEquals("http://127.0.0.1:37653/mcp", McpNetworkBinding.endpoint(37653, false), "default endpoint");
    }

    private static void usesAllInterfacesForLocalNetwork()
    {
        checkEquals("0.0.0.0", McpNetworkBinding.bindAddress(true), "local network bind address");
        checkEquals("http://0.0.0.0:37653/mcp", McpNetworkBinding.endpoint(37653, true), "local network endpoint");
    }

    private static void validatesOriginPolicy()
    {
        check(McpHttpServer.originAllowed(null, false), "missing origin allowed");
        check(McpHttpServer.originAllowed(List.of("http://localhost:3000"), false), "localhost origin allowed");
        check(McpHttpServer.originAllowed(List.of("http://127.0.0.1:3000"), false), "loopback origin allowed");
        check(!McpHttpServer.originAllowed(List.of("http://192.168.178.10:3000"), false),
            "private origin blocked by default");
        check(McpHttpServer.originAllowed(List.of("http://192.168.178.10:3000"), true),
            "192.168 origin allowed for local network");
        check(McpHttpServer.originAllowed(List.of("http://10.1.2.3:3000"), true),
            "10 origin allowed for local network");
        check(McpHttpServer.originAllowed(List.of("http://172.16.0.1:3000"), true),
            "172.16 origin allowed for local network");
        check(McpHttpServer.originAllowed(List.of("http://172.31.255.255:3000"), true),
            "172.31 origin allowed for local network");
        check(!McpHttpServer.originAllowed(List.of("http://172.32.0.1:3000"), true),
            "172.32 origin blocked");
        check(!McpHttpServer.originAllowed(List.of("http://8.8.8.8:3000"), true), "public origin blocked");
        check(!McpHttpServer.originAllowed(List.of("not a url"), true), "invalid origin blocked");
    }

    private static void check(boolean condition, String message)
    {
        if (!condition)
            throw new AssertionError(message);
    }

    private static void checkEquals(Object expected, Object actual, String message)
    {
        if (!Objects.equals(expected, actual))
            throw new AssertionError(message + " expected <" + expected + "> but was <" + actual + ">");
    }
}
