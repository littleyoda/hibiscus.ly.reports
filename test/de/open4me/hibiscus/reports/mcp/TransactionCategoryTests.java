package de.open4me.hibiscus.reports.mcp;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.willuhn.jameica.hbci.rmi.Umsatz;
import de.willuhn.jameica.hbci.rmi.UmsatzTyp;

public final class TransactionCategoryTests
{
    public static void run()
    {
        try
        {
            handlerContract();
            persistsAssignment();
            rejectsUnknownObjects();
            rollsBackFailedStore();
            batchAssignments();
        }
        catch (Exception e)
        {
            throw new AssertionError("Transaction category tests failed", e);
        }
    }

    private static void handlerContract() throws Exception
    {
        AtomicBoolean allowed = new AtomicBoolean(false);
        List<String> calls = new ArrayList<>();
        McpJsonRpcHandler handler = new McpJsonRpcHandler(null, null, null, null, null, () -> false,
            (transactionIds, categoryId) -> {
                List<TransactionCategoryWriter.Result> results = new ArrayList<>();
                for (String id : transactionIds)
                {
                    calls.add(id + ":" + categoryId);
                    results.add(new TransactionCategoryWriter.Result(id, "old", categoryId, !id.equals("3")));
                }
                return results;
            }, allowed::get);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode tools = mapper.readTree(handler.handle("{\"id\":1,\"method\":\"tools/list\"}"))
            .path("result").path("tools");
        JsonNode definition = null;
        for (JsonNode tool : tools)
            if (tool.path("name").asText().equals("hibiscus_transaction_category_set"))
                definition = tool;
        check(definition != null, "tool advertised");
        check(definition.path("inputSchema").path("required").get(0).asText().equals("categoryId"),
            "category required");
        check(definition.path("inputSchema").path("oneOf").size() == 2, "exclusive single or batch input");
        check(!definition.path("annotations").path("readOnlyHint").asBoolean(true), "write annotation");
        check(definition.path("annotations").path("idempotentHint").asBoolean(), "idempotent annotation");

        JsonNode denied = call(handler, Map.of("transactionId", "1", "categoryId", "2"));
        check(denied.path("error").path("data").asText().contains("deaktiviert"), "disabled by default");
        check(calls.isEmpty(), "disabled does not call writer");
        check(call(handler, Map.of("transactionIds", List.of("1", "3"), "categoryId", "2")).has("error"),
            "batch disabled by same permission");
        allowed.set(true);
        for (Map<String, Object> invalid : List.<Map<String, Object>>of(
            Map.of(), Map.of("transactionId", "1"), Map.of("transactionId", " ", "categoryId", "2"),
            Map.of("transactionId", 1, "categoryId", "2"), Map.of("transactionId", "1", "categoryId", false),
            Map.of("transactionIds", List.of(), "categoryId", "2"),
            Map.of("transactionIds", "1", "categoryId", "2"),
            Map.of("transactionIds", List.of("1", " "), "categoryId", "2"),
            Map.of("transactionIds", List.of("1", 3), "categoryId", "2"),
            Map.of("transactionIds", List.of("1", " 1 "), "categoryId", "2"),
            Map.of("transactionId", "1", "transactionIds", List.of("3"), "categoryId", "2")))
            check(call(handler, invalid).has("error"), "invalid IDs rejected");
        check(calls.isEmpty(), "invalid input does not call writer");
        JsonNode assigned = call(handler, Map.of("transactionId", " 1 ", "categoryId", "2"))
            .path("result").path("structuredContent");
        check(calls.equals(List.of("1:2")), "explicit IDs passed to writer");
        check(assigned.path("previousCategoryId").asText().equals("old"), "previous category returned");
        check(assigned.path("categoryId").asText().equals("2"), "new category returned");
        check(assigned.path("changed").asBoolean(), "change returned");
        JsonNode batch = call(handler, Map.of("transactionIds", List.of(" 1 ", "3"), "categoryId", "2"))
            .path("result").path("structuredContent");
        check(calls.equals(List.of("1:2", "1:2", "3:2")), "batch IDs passed in order");
        check(batch.path("count").asInt() == 2 && batch.path("changedCount").asInt() == 1,
            "batch counts include unchanged transactions");
        check(batch.path("assignments").get(1).path("transactionId").asText().equals("3"),
            "individual results returned");
        allowed.set(false);
        check(call(handler, Map.of("transactionId", "1", "categoryId", "2")).has("error"),
            "revocation applies immediately");
        check(call(handler, Map.of("transactionIds", List.of("1", "3"), "categoryId", "2")).has("error"),
            "batch revocation applies immediately");
        check(calls.size() == 3, "revoked writer not invoked");
    }

    private static JsonNode call(McpJsonRpcHandler handler, Map<String, Object> arguments) throws Exception
    {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readTree(handler.handle(mapper.writeValueAsString(Map.of("id", 1, "method", "tools/call",
            "params", Map.of("name", "hibiscus_transaction_category_set", "arguments", arguments)))));
    }

    private static void persistsAssignment() throws Exception
    {
        FakeTransaction transaction = new FakeTransaction();
        UmsatzTyp category = category("2");
        HibiscusTransactionCategoryWriter writer = new HibiscusTransactionCategoryWriter(
            id -> transaction.proxy(), id -> category);
        TransactionCategoryWriter.Result result = writer.assign("1", "2");
        check(result.previousCategoryId() == null && result.changed(), "uncategorized assignment");
        check(transaction.category == category, "category set");
        check(transaction.events.equals(List.of("begin", "set", "store", "commit")), "atomic write");
        transaction.events.clear();
        result = writer.assign("1", "2");
        check(!result.changed(), "same category is a no-op");
        check(transaction.events.equals(List.of("begin", "commit")), "no repeated store");
        transaction.events.clear();
        result = new HibiscusTransactionCategoryWriter(id -> transaction.proxy(), id -> category("3"))
            .assign("1", "3");
        check(Objects.equals("2", result.previousCategoryId()) && result.changed(), "existing category replaced");
    }

    private static void rejectsUnknownObjects() throws Exception
    {
        FakeTransaction transaction = new FakeTransaction();
        expectFailure(() -> new HibiscusTransactionCategoryWriter(id -> null, id -> category("2"))
            .assign("1", "2"));
        expectFailure(() -> new HibiscusTransactionCategoryWriter(id -> transaction.proxy(), id -> null)
            .assign("1", "2"));
        expectFailure(() -> new HibiscusTransactionCategoryWriter(id -> transaction.proxy(), id -> category("3"))
            .assign("1", "2"));
        expectFailure(() -> new HibiscusTransactionCategoryWriter(id -> transaction.proxy(), id -> category("2"))
            .assign("", "2"));
        check(transaction.events.isEmpty(), "unknown objects never mutate transaction");
    }

    private static void rollsBackFailedStore() throws Exception
    {
        FakeTransaction transaction = new FakeTransaction();
        transaction.failStore = true;
        expectFailure(() -> new HibiscusTransactionCategoryWriter(id -> transaction.proxy(), id -> category("2"))
            .assign("1", "2"));
        check(transaction.events.equals(List.of("begin", "set", "store", "rollback")), "failed store rolls back");
    }

    private static UmsatzTyp category(String id)
    {
        return (UmsatzTyp) Proxy.newProxyInstance(UmsatzTyp.class.getClassLoader(), new Class<?>[] { UmsatzTyp.class },
            (proxy, method, arguments) -> {
                if (method.getName().equals("getID"))
                    return id;
                throw new AssertionError("Unexpected category access: " + method.getName());
            });
    }

    private static void batchAssignments() throws Exception
    {
        FakeTransaction first = new FakeTransaction();
        FakeTransaction second = new FakeTransaction("3");
        UmsatzTyp target = category("2");
        second.category = target;
        HibiscusTransactionCategoryWriter writer = new HibiscusTransactionCategoryWriter(
            id -> id.equals("1") ? first.proxy() : id.equals("3") ? second.proxy() : null,
            id -> target);
        expectFailure(() -> writer.assignAll(List.of("1", "missing"), "2"));
        expectFailure(() -> writer.assignAll(List.of("1", "1"), "2"));
        expectFailure(() -> writer.assignAll(List.of(), "2"));
        check(first.events.isEmpty() && second.events.isEmpty(), "validate entire batch before mutation");
        List<TransactionCategoryWriter.Result> results = writer.assignAll(List.of("1", "3"), "2");
        check(results.size() == 2 && results.get(0).changed() && !results.get(1).changed(),
            "batch handles changed and unchanged transactions");
        check(first.events.equals(List.of("begin", "set", "store", "commit")) && second.events.isEmpty(),
            "one transaction for entire batch, unchanged item not stored");
        first.events.clear();
        first.category = null;
        second.category = null;
        second.failStore = true;
        expectFailure(() -> writer.assignAll(List.of("1", "3"), "2"));
        check(first.events.equals(List.of("begin", "set", "store", "rollback")),
            "later failure rolls back shared transaction instead of committing first item");
        check(second.events.equals(List.of("set", "store")), "second item uses existing transaction");
    }

    private static final class FakeTransaction
    {
        UmsatzTyp category;
        boolean failStore;
        final List<String> events = new ArrayList<>();
        final String id;

        FakeTransaction() { this("1"); }
        FakeTransaction(String id) { this.id = id; }

        Umsatz proxy()
        {
            return (Umsatz) Proxy.newProxyInstance(Umsatz.class.getClassLoader(), new Class<?>[] { Umsatz.class },
                (proxy, method, arguments) -> {
                    switch (method.getName())
                    {
                        case "getID": return id;
                        case "getUmsatzTyp": return category;
                        case "transactionBegin": events.add("begin"); return null;
                        case "transactionCommit": events.add("commit"); return null;
                        case "transactionRollback": events.add("rollback"); return null;
                        case "setUmsatzTyp": category = (UmsatzTyp) arguments[0]; events.add("set"); return null;
                        case "store":
                            events.add("store");
                            if (failStore)
                                throw new java.rmi.RemoteException("Test store failure");
                            return null;
                        default: throw new AssertionError("Unexpected transaction access: " + method.getName());
                    }
                });
        }
    }

    private interface Action { void run() throws Exception; }

    private static void expectFailure(Action action) throws Exception
    {
        try { action.run(); }
        catch (Exception expected) { return; }
        throw new AssertionError("Expected failure");
    }

    private static void check(boolean condition, String message)
    {
        if (!condition)
            throw new AssertionError(message);
    }
}
