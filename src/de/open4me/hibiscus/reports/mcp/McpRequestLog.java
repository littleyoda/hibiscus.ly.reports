package de.open4me.hibiscus.reports.mcp;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Session-only, bounded metadata log for requests to the MCP endpoint. */
public final class McpRequestLog
{
    private static final int CAPACITY = 1_000;
    private static final int LABEL_LENGTH = 120;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final McpRequestLog INSTANCE = new McpRequestLog(CAPACITY);

    public record Entry(Instant time, String source, String operation, int httpStatus,
                        String rpcError, long durationMillis) {}

    private final int capacity;
    private final ArrayDeque<Entry> entries = new ArrayDeque<>();
    private long version;

    McpRequestLog(int capacity)
    {
        if (capacity < 1)
            throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
    }

    public static McpRequestLog get()
    {
        return INSTANCE;
    }

    public synchronized long version()
    {
        return version;
    }

    public synchronized List<Entry> snapshot()
    {
        return List.copyOf(entries);
    }

    synchronized void add(String source, String operation, int httpStatus, String rpcError, long durationMillis)
    {
        if (entries.size() == capacity)
            entries.removeFirst();
        entries.addLast(new Entry(Instant.now(), source, operation, httpStatus, rpcError,
            Math.max(0, durationMillis)));
        version++;
    }

    static String operation(String body)
    {
        try
        {
            JsonNode request = JSON.readTree(body);
            if (request == null)
                return "Ungültiges JSON";
            if (!request.isArray())
                return operation(request);
            if (request.isEmpty())
                return "Leerer Batch";
            List<String> names = new ArrayList<>();
            int count = 0;
            for (JsonNode item : request)
            {
                if (count++ < 3)
                    names.add(operation(item));
            }
            String summary = "Batch: " + String.join(", ", names);
            if (count > 3)
                summary += " ( +" + (count - 3) + " )";
            return limit(summary);
        }
        catch (Exception e)
        {
            return "Ungültiges JSON";
        }
    }

    static String rpcError(String response)
    {
        if (response == null)
            return "";
        try
        {
            JsonNode value = JSON.readTree(response);
            if (value.isArray())
            {
                for (JsonNode item : value)
                {
                    String error = errorCode(item);
                    if (!error.isEmpty())
                        return error;
                }
                return "";
            }
            return errorCode(value);
        }
        catch (Exception e)
        {
            return "";
        }
    }

    private static String operation(JsonNode request)
    {
        String method = request.path("method").asText("");
        if (method.isBlank())
            return "Ungültige JSON-RPC-Anfrage";
        if ("tools/call".equals(method))
        {
            String name = request.path("params").path("name").asText("");
            if (!name.isBlank())
                return limit("tools/call: " + name);
        }
        return limit(method);
    }

    private static String errorCode(JsonNode response)
    {
        JsonNode code = response.path("error").path("code");
        return code.isIntegralNumber() ? "RPC " + code.asInt() : "";
    }

    private static String limit(String value)
    {
        String clean = value.replaceAll("[\\p{Cntrl}]", " ").trim();
        return clean.length() <= LABEL_LENGTH ? clean : clean.substring(0, LABEL_LENGTH - 1) + "…";
    }
}
