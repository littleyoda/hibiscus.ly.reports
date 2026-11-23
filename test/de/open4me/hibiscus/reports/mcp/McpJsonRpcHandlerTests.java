package de.open4me.hibiscus.reports.mcp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.open4me.hibiscus.reports.api.ReportTemplateContext;
import de.open4me.hibiscus.reports.data.ReportAccountProvider;
import de.open4me.hibiscus.reports.data.ReportCategoryProvider;
import de.open4me.hibiscus.reports.data.ReportTemplateContextFactory;
import de.open4me.hibiscus.reports.data.ReportTransactionProvider;
import de.open4me.hibiscus.reports.data.ReportTransactionQuery;
import de.open4me.hibiscus.reports.data.ReportTransactionsProxy;
import de.open4me.hibiscus.reports.model.CategoryInfo;
import de.open4me.hibiscus.reports.model.ReportAccount;
import de.open4me.hibiscus.reports.model.ReportTransaction;
import de.willuhn.jameica.gui.extension.Extension;
import de.willuhn.jameica.gui.extension.ExtensionRegistry;
import de.willuhn.jameica.hbci.gui.filter.KontoFilter;

public final class McpJsonRpcHandlerTests
{
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private McpJsonRpcHandlerTests()
    {
    }

    public static void run()
    {
        try
        {
            initializes();
            listsTools();
            listsToolSchemasWithMachineReadableHints();
            listsTransferSchemaTitles();
            listsAccounts();
            listsCategories();
            syncsSelectedAccounts();
            listsTransactionsWithFilters();
            listsTransactionsWithCategoryFilter();
            listsTransactionIds();
            rendersExtensionObjects();
            listsProviderTools();
            callsProviderTools();
            readsProviderResource();
            rejectsTransferDraftWhenWriteAccessIsDisabled();
            rejectsTransferDraftWithFriendlyRequiredFieldMessage();
            rejectsInvalidTransferDraft();
            createsTransferDraft();
            hidesDeleteToolWhenDisabled();
            listsDeleteToolWhenEnabled();
            deleteToolFollowsSettingWithoutRestart();
            rejectsDeleteWhenDisabled();
            rejectsDeleteWithoutIds();
            deleteDryRunReportsWithoutConfirmOrDelete();
            deletesConfirmedTransactionsPerId();
            declinedDeleteDeletesNothing();
            deleteConfirmFailureDeletesNothing();
            deleteWithOnlyUnknownIdsSkipsConfirmation();
            deleteLookupFailureOnlyAffectsThatId();
            deleteAcceptsNumericIds();
            rejectsNonBooleanDryRun();
            rejectsNonScalarIds();
            rejectsTooManyIds();
            deleteHandlesCandidateWithoutDate();
            deleteSkipsRefreshWhenNothingWasDeleted();
            deleteRefreshFailureKeepsResults();
            acceptsBooleanStringDryRun();
            messageLessConfirmFailureNamesExceptionClass();
        }
        catch (Exception e)
        {
            throw new AssertionError("MCP JSON-RPC tests failed", e);
        }
    }

    private static void initializes() throws Exception
    {
        JsonNode response = request("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}");
        checkEquals("2025-11-25", response.path("result").path("protocolVersion").asText(),
            "protocol version");
        checkEquals("hibiscus.ly.reports", response.path("result").path("serverInfo").path("name").asText(),
            "server name");
    }

    private static void listsTools() throws Exception
    {
        JsonNode response = request("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}");
        List<String> names = new ArrayList<>();
        response.path("result").path("tools").forEach(tool -> names.add(tool.path("name").asText()));
        check(names.contains("hibiscus_template_render"), "template render tool");
        check(names.contains("hibiscus_accounts_list"), "accounts tool");
        check(names.contains("hibiscus_accounts_sync"), "accounts sync tool");
        check(names.contains("hibiscus_categories_list"), "categories tool");
        check(names.contains("hibiscus_sepa_transfer_create"), "sepa transfer create tool");
        check(!names.contains("reports_read"), "no report file tool");
    }

    private static void listsToolSchemasWithMachineReadableHints() throws Exception
    {
        JsonNode tools = request("{\"jsonrpc\":\"2.0\",\"id\":14,\"method\":\"tools/list\"}")
            .path("result").path("tools");
        JsonNode accounts = schemaProperties(tools, "hibiscus_accounts_list");
        checkEquals("active", accounts.path("scope").path("enum").get(0).asText(), "accounts scope active");
        checkEquals("all", accounts.path("scope").path("enum").get(1).asText(), "accounts scope all");

        JsonNode transactions = schemaProperties(tools, "hibiscus_transactions_list");
        checkEquals("Kategorie-ID", transactions.path("categoryId").path("title").asText(),
            "transaction category title");
        check(transactions.path("includeSubcategories").path("description").asText().contains("Unterkategorien"),
            "transaction include subcategories description");
        checkEquals("date", transactions.path("from").path("format").asText(), "transaction from date format");
        check(transactions.path("from").path("description").asText().contains("YYYY-MM-DD"),
            "transaction from date description");
        checkEquals("date", transactions.path("to").path("format").asText(), "transaction to date format");
        check(transactions.path("all").path("description").asText().contains("90 Tage"),
            "transaction all description");

        JsonNode transfer = schemaProperties(tools, "hibiscus_sepa_transfer_create");
        checkEquals("date", transfer.path("executionDate").path("format").asText(),
            "transfer execution date format");
        check(transfer.path("amount").path("description").asText().contains("Dezimalkomma"),
            "transfer amount description");

        JsonNode categories = schemaProperties(tools, "hibiscus_categories_list");
        check(categories.path("includeSkipped").path("description").asText().contains("Standard ist true"),
            "categories include skipped description");
    }

    private static void listsTransferSchemaTitles() throws Exception
    {
        JsonNode response = request("{\"jsonrpc\":\"2.0\",\"id\":12,\"method\":\"tools/list\"}");
        JsonNode tools = response.path("result").path("tools");
        JsonNode schema = null;
        for (JsonNode tool : tools)
        {
            if ("hibiscus_sepa_transfer_create".equals(tool.path("name").asText()))
            {
                schema = tool.path("inputSchema").path("properties");
                break;
            }
        }
        check(schema != null && !schema.isMissingNode(), "transfer schema found");
        checkEquals("Auftraggeberkonto", schema.path("accountId").path("title").asText(), "account title");
        checkEquals("Empfänger-IBAN", schema.path("recipientIban").path("title").asText(), "iban title");
        checkEquals("Betrag", schema.path("amount").path("title").asText(), "amount title");
        checkEquals("Überweisungsart", schema.path("type").path("title").asText(), "type title");
    }

    private static void listsAccounts() throws Exception
    {
        JsonNode response = request("""
            {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{
              "name":"hibiscus_accounts_list",
              "arguments":{"scope":"all"}
            }}
            """);
        JsonNode accounts = response.path("result").path("structuredContent").path("accounts");
        checkEquals(3, accounts.size(), "account count");
        checkEquals("Archivkonto", accounts.get(2).path("name").asText(), "all accounts include archive");
        check(!accounts.get(2).path("aktiv").asBoolean(), "active flag");
        check(accounts.get(2).path("offline").asBoolean(), "offline flag");
    }

    private static void listsCategories() throws Exception
    {
        JsonNode response = request("""
            {"jsonrpc":"2.0","id":15,"method":"tools/call","params":{
              "name":"hibiscus_categories_list",
              "arguments":{"includeSkipped":false}
            }}
            """);
        JsonNode categories = response.path("result").path("structuredContent").path("categories");
        checkEquals(2, categories.size(), "category count");
        JsonNode child = categories.get(1);
        checkEquals("14", child.path("id").asText(), "category id");
        checkEquals("13", child.path("parentId").asText(), "category parent id");
        checkEquals("Food > Restaurant", child.path("path").asText(), "category path");
        checkEquals("13", child.path("pathIds").get(0).asText(), "category path root id");
        checkEquals("14", child.path("pathIds").get(1).asText(), "category path leaf id");
        checkEquals("Nur Restaurant", child.path("notiz").asText(), "category note");
        checkEquals("restaurant.*", child.path("suchbegriff").asText(), "category pattern");
        check(child.path("regex").asBoolean(), "category regex flag");
        checkEquals("ausgabe", child.path("typ").asText(), "category type");
        checkEquals("#654321", child.path("colorHex").asText(), "category color");
        check(!child.path("skipReportsEffective").asBoolean(), "effective skip flag");
        checkEquals("hibiscus_transactions_list", child.path("transactionsTool").asText(),
            "category transactions tool");
    }

    private static void syncsSelectedAccounts() throws Exception
    {
        FakeTransactionProvider transactions = new FakeTransactionProvider();
        FakeAccountSynchronizer sync = new FakeAccountSynchronizer();
        McpJsonRpcHandler handler = handler(transactions, new FakeTransferDraftWriter(), sync, true);

        JsonNode response = MAPPER.readTree(handler.handle("""
            {"jsonrpc":"2.0","id":13,"method":"tools/call","params":{
              "name":"hibiscus_accounts_sync",
              "arguments":{"accountIds":["active","daily"]}
            }}
            """));

        check(!sync.all, "selected account sync");
        checkEquals(List.of("active", "daily"), sync.accountIds(), "synced account ids");
        JsonNode result = response.path("result").path("structuredContent");
        check(result.path("gestartet").asBoolean(), "sync started");
        checkEquals(2, result.path("count").asInt(), "sync result count");
    }

    private static void listsTransactionsWithFilters() throws Exception
    {
        FakeTransactionProvider transactions = new FakeTransactionProvider();
        McpJsonRpcHandler handler = handler(transactions);
        JsonNode response = MAPPER.readTree(handler.handle("""
            {"jsonrpc":"2.0","id":4,"method":"tools/call","params":{
              "name":"hibiscus_transactions_list",
              "arguments":{"accountId":"active","from":"2026-01-01","to":"2026-01-31","limit":1}
            }}
            """));
        checkEquals(1, response.path("result").path("structuredContent").path("transactions").size(),
            "transaction count");
        ReportTransactionQuery query = transactions.queries.get(0);
        checkEquals("active", query.accountId(), "query account");
        checkEquals(LocalDate.of(2026, 1, 1), query.from(), "query from");
        checkEquals(LocalDate.of(2026, 1, 31), query.to(), "query to");
        checkEquals(Integer.valueOf(1), query.limit(), "query limit");
    }

    private static void listsTransactionsWithCategoryFilter() throws Exception
    {
        FakeTransactionProvider transactions = new FakeTransactionProvider();
        McpJsonRpcHandler handler = handler(transactions);
        JsonNode response = MAPPER.readTree(handler.handle("""
            {"jsonrpc":"2.0","id":16,"method":"tools/call","params":{
              "name":"hibiscus_transactions_list",
              "arguments":{"categoryId":"13","includeSubcategories":true}
            }}
            """));
        checkEquals(2, response.path("result").path("structuredContent").path("transactions").size(),
            "category transaction count");
        ReportTransactionQuery query = transactions.queries.get(0);
        checkEquals(List.of("13"), query.categoryIds(), "query category ids");
        check(query.includeSubcategories(), "query include subcategories");
    }

    private static void listsTransactionIds() throws Exception
    {
        McpJsonRpcHandler handler = handler(new FakeTransactionProvider());
        JsonNode response = MAPPER.readTree(handler.handle("""
            {"jsonrpc":"2.0","id":17,"method":"tools/call","params":{
              "name":"hibiscus_transactions_list",
              "arguments":{}
            }}
            """));
        JsonNode transactions = response.path("result").path("structuredContent").path("transactions");
        checkEquals(3, transactions.size(), "transaction id count");
        check(transactions.get(0).path("id").isTextual(), "transaction id is string");
        checkEquals("13", transactions.get(0).path("id").asText(), "transaction id");
        checkEquals("20", transactions.get(2).path("id").asText(), "last transaction id");
    }

    private static void rendersExtensionObjects() throws Exception
    {
        Extension extension = extendable -> ((ReportTemplateContext) extendable).put("extern",
            new ExternalObject("Depotviewer"));
        ExtensionRegistry.register(extension, ReportTemplateContext.EXTENDABLE_ID);
        try
        {
            JsonNode response = request("""
                {"jsonrpc":"2.0","id":5,"method":"tools/call","params":{
                  "name":"hibiscus_template_render",
                  "arguments":{"template":"{{ extern.name }}"}
                }}
                """);
            checkEquals("Depotviewer", response.path("result").path("structuredContent").path("html").asText(),
                "render extension object");
        }
        finally
        {
            List<Extension> extensions = ExtensionRegistry.getExtensions(ReportTemplateContext.EXTENDABLE_ID);
            if (extensions != null)
                extensions.remove(extension);
        }
    }

    private static void listsProviderTools() throws Exception
    {
        withProviderExtension(() -> {
            JsonNode response = request("{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"tools/list\"}");
            List<String> names = new ArrayList<>();
            response.path("result").path("tools").forEach(tool -> names.add(tool.path("name").asText()));
            check(names.contains("fake_items_list"), "provider tool listed");
        });
    }

    private static void callsProviderTools() throws Exception
    {
        withProviderExtension(() -> {
            JsonNode response = request("""
                {"jsonrpc":"2.0","id":7,"method":"tools/call","params":{
                  "name":"fake_items_list",
                  "arguments":{"limit":2}
                }}
                """);
            JsonNode content = response.path("result").path("structuredContent");
            checkEquals(2, content.path("items").size(), "provider item count");
            checkEquals("eins", content.path("items").get(0).path("name").asText(), "provider item value");
        });
    }

    private static void readsProviderResource() throws Exception
    {
        withProviderExtension(() -> {
            JsonNode response = request("""
                {"jsonrpc":"2.0","id":8,"method":"resources/read","params":{
                  "uri":"hibiscus-reports://mcp/providers"
                }}
                """);
            String text = response.path("result").path("contents").get(0).path("text").asText();
            check(text.contains("fake"), "provider resource namespace");
            check(text.contains("fake_items_list"), "provider resource tool");
        });
    }

    private static void rejectsTransferDraftWhenWriteAccessIsDisabled() throws Exception
    {
        McpJsonRpcHandler handler = handler(new FakeTransactionProvider(), new FakeTransferDraftWriter(), false);
        JsonNode response = MAPPER.readTree(handler.handle("""
            {"jsonrpc":"2.0","id":9,"method":"tools/call","params":{
              "name":"hibiscus_sepa_transfer_create",
              "arguments":{
                "accountId":"active",
                "recipientName":"Max Mustermann",
                "recipientIban":"DE02120300000000202051",
                "amount":12.34
              }
            }}
            """));
        check(response.has("error"), "disabled write access rejected");
        check(response.path("error").path("data").asText().contains("Ueberweisungen anlegen"),
            "disabled transfer creation message");
    }

    private static void rejectsInvalidTransferDraft() throws Exception
    {
        McpJsonRpcHandler handler = handler(new FakeTransactionProvider(), new FakeTransferDraftWriter(), true);
        JsonNode response = MAPPER.readTree(handler.handle("""
            {"jsonrpc":"2.0","id":10,"method":"tools/call","params":{
              "name":"hibiscus_sepa_transfer_create",
              "arguments":{
                "accountId":"active",
                "recipientName":"Max Mustermann",
                "recipientIban":"DE02120300000000202051",
                "amount":-1
              }
            }}
            """));
        check(response.has("error"), "invalid transfer rejected");
        check(response.path("error").path("data").asText().contains("Betrag"), "invalid amount message");
    }

    private static void rejectsTransferDraftWithFriendlyRequiredFieldMessage() throws Exception
    {
        McpJsonRpcHandler handler = handler(new FakeTransactionProvider(), new FakeTransferDraftWriter(), true);
        JsonNode response = MAPPER.readTree(handler.handle("""
            {"jsonrpc":"2.0","id":13,"method":"tools/call","params":{
              "name":"hibiscus_sepa_transfer_create",
              "arguments":{
                "accountId":"active",
                "recipientName":"Max Mustermann",
                "amount":12.34
              }
            }}
            """));
        check(response.has("error"), "missing iban rejected");
        check(response.path("error").path("data").asText().contains("Empfänger-IBAN"),
            "missing iban message");
    }

    private static void createsTransferDraft() throws Exception
    {
        FakeTransferDraftWriter writer = new FakeTransferDraftWriter();
        McpJsonRpcHandler handler = handler(new FakeTransactionProvider(), writer, true);
        JsonNode response = MAPPER.readTree(handler.handle("""
            {"jsonrpc":"2.0","id":11,"method":"tools/call","params":{
              "name":"hibiscus_sepa_transfer_create",
              "arguments":{
                "accountId":"active",
                "recipientName":"Max Mustermann",
                "recipientIban":"DE02 1203 0000 0000 2020 51",
                "recipientBic":"BYLADEM1001",
                "amount":"12,34",
                "purpose":"Rechnung 123",
                "executionDate":"2026-07-12",
                "type":"Echtzeitüberweisung"
              }
            }}
            """));
        JsonNode draft = response.path("result").path("structuredContent");
        checkEquals("draft-1", draft.path("id").asText(), "draft id");
        checkEquals("DE02120300000000202051", writer.request.recipientIban(), "iban compacted");
        checkEquals(new BigDecimal("12.34"), writer.request.amount(), "amount parsed");
        checkEquals(LocalDate.of(2026, 7, 12), writer.request.executionDate(), "execution date");
        checkEquals("Echtzeitüberweisung", draft.path("type").asText(), "transfer type");
        check(draft.path("created").asBoolean(), "created flag");
        checkEquals("Entwurf angelegt", draft.path("status").asText(), "status text");
        check(!draft.path("sent").asBoolean(), "not sent");
        checkEquals("Nicht an die Bank gesendet", draft.path("sendStatus").asText(), "send status text");
    }

    private static void hidesDeleteToolWhenDisabled() throws Exception
    {
        McpJsonRpcHandler handler = handler(new FakeTransactionProvider(), new FakeTransactionDeleter(), () -> false);
        check(!toolNames(handler).contains("hibiscus_transactions_delete"), "delete tool hidden");
    }

    private static void listsDeleteToolWhenEnabled() throws Exception
    {
        McpJsonRpcHandler handler = handler(new FakeTransactionProvider(), new FakeTransactionDeleter(), () -> true);
        JsonNode tools = MAPPER.readTree(handler.handle("{\"jsonrpc\":\"2.0\",\"id\":30,\"method\":\"tools/list\"}"))
            .path("result").path("tools");
        List<String> names = new ArrayList<>();
        tools.forEach(tool -> names.add(tool.path("name").asText()));
        check(names.contains("hibiscus_transactions_delete"), "delete tool listed");
        JsonNode props = schemaProperties(tools, "hibiscus_transactions_delete");
        checkEquals("array", props.path("ids").path("type").asText(), "delete ids type");
        checkEquals(McpJsonRpcHandler.MAX_DELETE_IDS, props.path("ids").path("maxItems").asInt(),
            "delete ids maxItems matches the enforced limit");
        checkEquals("boolean", props.path("dryRun").path("type").asText(), "delete dryRun type");
        String description = "";
        for (JsonNode tool : tools)
            if ("hibiscus_transactions_delete".equals(tool.path("name").asText()))
                description = tool.path("description").asText();
        check(description.contains("Der Aufruf wartet bis zu "
                + HibiscusTransactionDeleter.CONFIRM_TIMEOUT.toSeconds() + " Sekunden auf die Bestaetigung."),
            "delete description mentions confirmation wait from the timeout constant: " + description);
        checkEquals(50L, HibiscusTransactionDeleter.CONFIRM_TIMEOUT.toSeconds(),
            "confirmation timeout stays below the usual 60 s client limit");
    }

    private static void rejectsNonScalarIds() throws Exception
    {
        FakeTransactionDeleter deleter = deleterWithExisting("7");
        McpJsonRpcHandler handler = handler(new FakeTransactionProvider(), deleter, () -> true);
        for (String arguments : List.of("{\"ids\":[{\"id\":\"7\"}]}", "{\"ids\":[[\"7\"]]}", "{\"ids\":[null]}"))
        {
            JsonNode response = callDelete(handler, arguments);
            check(response.has("error"), "non-scalar id rejected: " + arguments);
            check(response.path("error").path("data").asText().contains("nur Umsatz-IDs als Text oder Zahl"),
                "non-scalar id message: " + arguments);
        }
        checkNoDeleterCalls(deleter);
    }

    private static void rejectsTooManyIds() throws Exception
    {
        FakeTransactionDeleter deleter = new FakeTransactionDeleter();
        McpJsonRpcHandler handler = handler(new FakeTransactionProvider(), deleter, () -> true);
        StringBuilder ids = new StringBuilder();
        for (int i = 1; i <= 201; i++)
            ids.append(i == 1 ? "" : ",").append('"').append(i).append('"');
        JsonNode response = callDelete(handler, "{\"ids\":[" + ids + "]}");
        check(response.has("error"), "more than 200 ids rejected");
        check(response.path("error").path("data").asText().contains("hoechstens 200 Umsatz-IDs"),
            "too many ids message");
        checkNoDeleterCalls(deleter);

        StringBuilder twoHundred = new StringBuilder();
        for (int i = 1; i <= 200; i++)
            twoHundred.append(i == 1 ? "" : ",").append('"').append(i).append('"');
        check(!callDelete(handler, "{\"ids\":[" + twoHundred + "],\"dryRun\":true}").has("error"),
            "exactly 200 ids accepted");
    }

    private static void deleteHandlesCandidateWithoutDate() throws Exception
    {
        FakeTransactionDeleter deleter = new FakeTransactionDeleter();
        deleter.existing.put("1", new TransactionDeleter.Candidate("1", "7", "Beispielkonto", null, -42.0, "EUR",
            "Beispiel GmbH", "Rechnung 4711", List.of()));
        McpJsonRpcHandler handler = handler(new FakeTransactionProvider(), deleter, () -> true);
        JsonNode entry = result(callDelete(handler, "{\"ids\":[\"1\"],\"dryRun\":true}")).path("results").get(0);
        checkEquals("wouldDelete", entry.path("status").asText(), "undated transaction can be deleted");
        check(entry.path("transaction").path("datum").isNull(), "undated transaction reports datum null");
    }

    private static void deleteToolFollowsSettingWithoutRestart() throws Exception
    {
        FakeTransactionDeleter deleter = new FakeTransactionDeleter();
        deleter.existing.put("1", candidate("1"));
        deleter.answer = true;
        AtomicBoolean enabled = new AtomicBoolean(false);
        McpJsonRpcHandler handler = handler(new FakeTransactionProvider(), deleter, enabled::get);
        check(!toolNames(handler).contains("hibiscus_transactions_delete"), "delete tool absent while off");
        check(callDelete(handler, "{\"ids\":[\"1\"]}").has("error"), "delete call refused while off");
        enabled.set(true);
        check(toolNames(handler).contains("hibiscus_transactions_delete"), "delete tool present after enabling");
        JsonNode response = callDelete(handler, "{\"ids\":[\"1\"]}");
        check(!response.has("error"), "delete call succeeds after enabling");
        checkEquals("deleted", result(response).path("results").get(0).path("status").asText(),
            "status after enabling");
    }

    private static void rejectsDeleteWhenDisabled() throws Exception
    {
        FakeTransactionDeleter deleter = new FakeTransactionDeleter();
        McpJsonRpcHandler handler = handler(new FakeTransactionProvider(), deleter, () -> false);
        JsonNode response = callDelete(handler, "{\"ids\":[\"1\"]}");
        check(response.has("error"), "disabled delete rejected");
        check(response.path("error").path("data").asText().contains("Umsaetze loeschen"),
            "disabled delete message");
        checkNoDeleterCalls(deleter);
    }

    private static void rejectsDeleteWithoutIds() throws Exception
    {
        FakeTransactionDeleter deleter = new FakeTransactionDeleter();
        McpJsonRpcHandler handler = handler(new FakeTransactionProvider(), deleter, () -> true);
        for (String arguments : List.of("{}", "{\"ids\":[]}"))
        {
            JsonNode response = callDelete(handler, arguments);
            check(response.has("error"), "delete without ids rejected: " + arguments);
            check(response.path("error").path("data").asText().contains("mindestens eine Umsatz-ID"),
                "delete without ids message: " + arguments);
        }
        checkNoDeleterCalls(deleter);
    }

    private static void deleteDryRunReportsWithoutConfirmOrDelete() throws Exception
    {
        FakeTransactionDeleter deleter = deleterWithExisting("1", "2");
        McpJsonRpcHandler handler = handler(new FakeTransactionProvider(), deleter, () -> true);
        JsonNode result = result(callDelete(handler, "{\"ids\":[\"1\",\"2\",\"x\"],\"dryRun\":true}"));
        JsonNode results = result.path("results");
        checkEquals("wouldDelete", results.get(0).path("status").asText(), "dry run status 1");
        checkEquals("wouldDelete", results.get(1).path("status").asText(), "dry run status 2");
        checkEquals("unknown", results.get(2).path("status").asText(), "dry run status x");
        check(deleter.confirmCalls.isEmpty(), "dry run does not confirm");
        check(deleter.deleted.isEmpty(), "dry run does not delete");
        checkEquals(0, deleter.refreshCalls, "dry run does not refresh views");
        check(result.path("dryRun").asBoolean(), "dry run flag");
        check(!result.has("confirmed"), "dry run has no confirmed");
        checkEquals(2, result.path("counts").path("wouldDelete").asInt(), "dry run wouldDelete count");
        checkEquals(1, result.path("counts").path("unknown").asInt(), "dry run unknown count");
        checkEquals("kategorie: Versicherung", results.get(0).path("links").get(0).asText(), "dry run link");
        checkEquals("Beispiel GmbH", results.get(0).path("transaction").path("gegenkontoName").asText(),
            "dry run counterparty");
        checkEquals("EUR", results.get(0).path("transaction").path("waehrung").asText(), "dry run currency");
    }

    private static void deletesConfirmedTransactionsPerId() throws Exception
    {
        FakeTransactionDeleter deleter = deleterWithExisting("1", "2");
        deleter.failingDeletes.add("2");
        deleter.answer = true;
        McpJsonRpcHandler handler = handler(new FakeTransactionProvider(), deleter, () -> true);
        JsonNode result = result(callDelete(handler, "{\"ids\":[\"1\",\"2\",\"1\",\"x\"]}"));
        JsonNode results = result.path("results");
        checkEquals(3, results.size(), "distinct result entries");
        checkEquals("1", results.get(0).path("id").asText(), "result order 1");
        checkEquals("2", results.get(1).path("id").asText(), "result order 2");
        checkEquals("x", results.get(2).path("id").asText(), "result order x");
        checkEquals("deleted", results.get(0).path("status").asText(), "status 1");
        checkEquals("error", results.get(1).path("status").asText(), "status 2");
        check(results.get(1).path("message").asText().contains("Loeschen fehlgeschlagen"), "error message 2");
        checkEquals("unknown", results.get(2).path("status").asText(), "status x");
        checkEquals(1, deleter.confirmCalls.size(), "confirm called once");
        checkEquals(2, deleter.confirmCalls.get(0).size(), "confirm candidates");
        checkEquals(Integer.valueOf(1), deleter.unknownCounts.get(0), "confirm unknown count");
        Instant requestStart = deleter.requestStarts.get(0);
        check(requestStart != null && !requestStart.isAfter(Instant.now()),
            "confirm gets the start of the request, so the dialog timeout includes the lookup time");
        checkEquals(List.of("1"), deleter.deleted, "deleted ids");
        checkEquals(1, deleter.refreshCalls, "open views refreshed once after deleting");
        check(result.path("confirmed").isBoolean() && result.path("confirmed").asBoolean(), "confirmed true");
        check(!result.path("dryRun").asBoolean(), "dryRun false");
        JsonNode counts = result.path("counts");
        checkEquals(1, counts.path("deleted").asInt(), "deleted count");
        checkEquals(1, counts.path("error").asInt(), "error count");
        checkEquals(1, counts.path("unknown").asInt(), "unknown count");
        checkEquals(0, counts.path("notConfirmed").asInt(), "notConfirmed count");
        checkEquals(0, counts.path("wouldDelete").asInt(), "wouldDelete count");
    }

    private static void deleteSkipsRefreshWhenNothingWasDeleted() throws Exception
    {
        FakeTransactionDeleter deleter = deleterWithExisting("1");
        deleter.failingDeletes.add("1");
        deleter.answer = true;
        McpJsonRpcHandler handler = handler(new FakeTransactionProvider(), deleter, () -> true);
        JsonNode result = result(callDelete(handler, "{\"ids\":[\"1\"]}"));
        checkEquals("error", result.path("results").get(0).path("status").asText(), "failed delete status");
        checkEquals(0, deleter.refreshCalls, "no refresh when nothing was deleted");
    }

    private static void deleteRefreshFailureKeepsResults() throws Exception
    {
        FakeTransactionDeleter deleter = deleterWithExisting("1");
        deleter.answer = true;
        deleter.failingRefresh = true;
        McpJsonRpcHandler handler = handler(new FakeTransactionProvider(), deleter, () -> true);
        JsonNode result = result(callDelete(handler, "{\"ids\":[\"1\"]}"));
        checkEquals(1, deleter.refreshCalls, "refresh attempted");
        checkEquals("deleted", result.path("results").get(0).path("status").asText(),
            "deletion still reported after refresh failure");
        checkEquals(1, result.path("counts").path("deleted").asInt(), "deleted count after refresh failure");
    }

    private static void declinedDeleteDeletesNothing() throws Exception
    {
        FakeTransactionDeleter deleter = deleterWithExisting("1", "2");
        deleter.answer = false;
        McpJsonRpcHandler handler = handler(new FakeTransactionProvider(), deleter, () -> true);
        JsonNode result = result(callDelete(handler, "{\"ids\":[\"1\",\"2\",\"x\"]}"));
        JsonNode results = result.path("results");
        checkEquals("notConfirmed", results.get(0).path("status").asText(), "declined status 1");
        checkEquals("notConfirmed", results.get(1).path("status").asText(), "declined status 2");
        checkEquals("unknown", results.get(2).path("status").asText(), "declined status x");
        check(deleter.deleted.isEmpty(), "declined deletes nothing");
        checkEquals(0, deleter.refreshCalls, "declined does not refresh views");
        check(result.path("confirmed").isBoolean() && !result.path("confirmed").asBoolean(), "confirmed false");
        check(!result.has("confirmError"), "no confirmError when declined");
        checkEquals(2, result.path("counts").path("notConfirmed").asInt(), "declined notConfirmed count");
        checkEquals(0, result.path("counts").path("deleted").asInt(), "declined deleted count");
    }

    private static void deleteConfirmFailureDeletesNothing() throws Exception
    {
        FakeTransactionDeleter deleter = deleterWithExisting("1", "2");
        deleter.answer = null;
        McpJsonRpcHandler handler = handler(new FakeTransactionProvider(), deleter, () -> true);
        JsonNode result = result(callDelete(handler, "{\"ids\":[\"1\",\"2\"]}"));
        checkEquals("notConfirmed", result.path("results").get(0).path("status").asText(),
            "failed confirm status 1");
        checkEquals("notConfirmed", result.path("results").get(1).path("status").asText(),
            "failed confirm status 2");
        check(result.path("confirmError").asText().contains("kein GUI"), "confirm error message");
        check(result.path("confirmed").isBoolean() && !result.path("confirmed").asBoolean(),
            "confirmed false after confirm failure");
        check(deleter.deleted.isEmpty(), "failed confirm deletes nothing");
    }

    private static void deleteWithOnlyUnknownIdsSkipsConfirmation() throws Exception
    {
        FakeTransactionDeleter deleter = new FakeTransactionDeleter();
        deleter.answer = true;
        McpJsonRpcHandler handler = handler(new FakeTransactionProvider(), deleter, () -> true);
        JsonNode result = result(callDelete(handler, "{\"ids\":[\"x\",\"y\"]}"));
        checkEquals("unknown", result.path("results").get(0).path("status").asText(), "unknown status x");
        checkEquals("unknown", result.path("results").get(1).path("status").asText(), "unknown status y");
        check(deleter.confirmCalls.isEmpty(), "no confirm for unknown ids");
        check(deleter.deleted.isEmpty(), "no delete for unknown ids");
        check(!result.has("confirmed"), "no confirmed for unknown ids");
        checkEquals(2, result.path("counts").path("unknown").asInt(), "unknown count");
    }

    private static void deleteLookupFailureOnlyAffectsThatId() throws Exception
    {
        FakeTransactionDeleter deleter = deleterWithExisting("1");
        deleter.failingLookups.add("3");
        deleter.answer = true;
        McpJsonRpcHandler handler = handler(new FakeTransactionProvider(), deleter, () -> true);
        JsonNode results = result(callDelete(handler, "{\"ids\":[\"3\",\"1\"]}")).path("results");
        checkEquals("error", results.get(0).path("status").asText(), "lookup failure status");
        check(results.get(0).path("message").asText().contains("Suche fehlgeschlagen"), "lookup failure message");
        checkEquals("deleted", results.get(1).path("status").asText(), "other id still deleted");
        checkEquals(Integer.valueOf(1), deleter.unknownCounts.get(0),
            "dialog counts the failed lookup among the skipped ids");
    }

    private static void deleteAcceptsNumericIds() throws Exception
    {
        FakeTransactionDeleter deleter = deleterWithExisting("1");
        deleter.answer = true;
        McpJsonRpcHandler handler = handler(new FakeTransactionProvider(), deleter, () -> true);
        JsonNode results = result(callDelete(handler, "{\"ids\":[1]}")).path("results");
        checkEquals("1", results.get(0).path("id").asText(), "numeric id as string");
        check(results.get(0).path("id").isTextual(), "numeric id output is string");
        checkEquals("deleted", results.get(0).path("status").asText(), "numeric id status");
    }

    private static void rejectsNonBooleanDryRun() throws Exception
    {
        FakeTransactionDeleter deleter = deleterWithExisting("1");
        deleter.answer = true;
        McpJsonRpcHandler handler = handler(new FakeTransactionProvider(), deleter, () -> true);
        for (String value : List.of("\"yes\"", "1", "0", "\"\"", "{}", "[]"))
        {
            JsonNode response = callDelete(handler, "{\"ids\":[\"1\"],\"dryRun\":" + value + "}");
            check(response.has("error"), "non-boolean dryRun rejected: " + value);
            check(response.path("error").path("data").asText().contains("dryRun: true oder false angeben."),
                "non-boolean dryRun message: " + value + " " + response.path("error"));
        }
        checkNoDeleterCalls(deleter);
    }

    private static void acceptsBooleanStringDryRun() throws Exception
    {
        FakeTransactionDeleter deleter = deleterWithExisting("1");
        McpJsonRpcHandler handler = handler(new FakeTransactionProvider(), deleter, () -> true);
        JsonNode result = result(callDelete(handler, "{\"ids\":[\"1\"],\"dryRun\":\"true\"}"));
        check(result.path("dryRun").asBoolean(), "string true is a dry run");
        check(deleter.confirmCalls.isEmpty() && deleter.deleted.isEmpty(), "string true dry run touches nothing");
    }

    private static void messageLessConfirmFailureNamesExceptionClass() throws Exception
    {
        FakeTransactionDeleter deleter = deleterWithExisting("1");
        deleter.answer = null;
        deleter.silentFailure = true;
        McpJsonRpcHandler handler = handler(new FakeTransactionProvider(), deleter, () -> true);
        JsonNode result = result(callDelete(handler, "{\"ids\":[\"1\"]}"));
        checkEquals("IllegalStateException", result.path("confirmError").asText(), "message-less confirm error");
        check(deleter.deleted.isEmpty(), "message-less confirm failure deletes nothing");
    }

    private static JsonNode callDelete(McpJsonRpcHandler handler, String arguments) throws Exception
    {
        return MAPPER.readTree(handler.handle("{\"jsonrpc\":\"2.0\",\"id\":31,\"method\":\"tools/call\","
            + "\"params\":{\"name\":\"hibiscus_transactions_delete\",\"arguments\":" + arguments + "}}"));
    }

    private static JsonNode result(JsonNode response)
    {
        check(!response.has("error"), "unexpected error: " + response.path("error"));
        return response.path("result").path("structuredContent");
    }

    private static List<String> toolNames(McpJsonRpcHandler handler) throws Exception
    {
        JsonNode tools = MAPPER.readTree(handler.handle("{\"jsonrpc\":\"2.0\",\"id\":32,\"method\":\"tools/list\"}"))
            .path("result").path("tools");
        List<String> names = new ArrayList<>();
        tools.forEach(tool -> names.add(tool.path("name").asText()));
        return names;
    }

    private static void checkNoDeleterCalls(FakeTransactionDeleter deleter)
    {
        check(deleter.lookups.isEmpty(), "deleter lookups untouched");
        check(deleter.confirmCalls.isEmpty(), "deleter confirm untouched");
        check(deleter.deleted.isEmpty(), "deleter delete untouched");
    }

    private static FakeTransactionDeleter deleterWithExisting(String... ids)
    {
        FakeTransactionDeleter deleter = new FakeTransactionDeleter();
        for (String id : ids)
            deleter.existing.put(id, candidate(id));
        return deleter;
    }

    private static TransactionDeleter.Candidate candidate(String id)
    {
        return new TransactionDeleter.Candidate(id, "7", "Beispielkonto", LocalDate.of(2025, 1, 14), -42.0, "EUR",
            "Beispiel GmbH", "Rechnung 4711", "1".equals(id) ? List.of("kategorie: Versicherung") : List.of());
    }

    private static void withProviderExtension(ThrowingRunnable runnable) throws Exception
    {
        Extension extension = extendable -> {
            if (extendable instanceof ReportMcpToolRegistry registry)
                registry.register(new FakeMcpProvider());
        };
        ExtensionRegistry.register(extension, ReportMcpToolRegistry.EXTENDABLE_ID);
        try
        {
            runnable.run();
        }
        finally
        {
            List<Extension> extensions = ExtensionRegistry.getExtensions(ReportMcpToolRegistry.EXTENDABLE_ID);
            if (extensions != null)
                extensions.remove(extension);
        }
    }

    private static JsonNode request(String request) throws Exception
    {
        return MAPPER.readTree(handler(new FakeTransactionProvider()).handle(request));
    }

    private static JsonNode schemaProperties(JsonNode tools, String name)
    {
        for (JsonNode tool : tools)
        {
            if (name.equals(tool.path("name").asText()))
                return tool.path("inputSchema").path("properties");
        }
        throw new AssertionError("missing tool schema " + name);
    }

    private static McpJsonRpcHandler handler(ReportTransactionProvider transactions)
    {
        return new McpJsonRpcHandler(new ReportTemplateContextFactory(new FakeAccountProvider(transactions),
            transactions), transactions, new FakeCategoryProvider(), new FakeTransferDraftWriter(),
            () -> false);
    }

    private static McpJsonRpcHandler handler(ReportTransactionProvider transactions,
                                             SepaTransferDraftWriter transferDraftWriter,
                                             boolean writeEnabled)
    {
        return new McpJsonRpcHandler(new ReportTemplateContextFactory(new FakeAccountProvider(transactions),
            transactions), transactions, new FakeCategoryProvider(), transferDraftWriter, () -> writeEnabled);
    }

    private static McpJsonRpcHandler handler(ReportTransactionProvider transactions,
                                             SepaTransferDraftWriter transferDraftWriter,
                                             McpAccountSynchronizer accountSynchronizer,
                                             boolean writeEnabled)
    {
        return new McpJsonRpcHandler(new ReportTemplateContextFactory(new FakeAccountProvider(transactions),
            transactions), transactions, new FakeCategoryProvider(), transferDraftWriter, accountSynchronizer,
            () -> writeEnabled);
    }

    private static McpJsonRpcHandler handler(ReportTransactionProvider transactions,
                                             TransactionDeleter transactionDeleter,
                                             BooleanSupplier deleteEnabled)
    {
        return new McpJsonRpcHandler(new ReportTemplateContextFactory(new FakeAccountProvider(transactions),
            transactions), transactions, new FakeCategoryProvider(), new FakeTransferDraftWriter(), null,
            () -> false, transactionDeleter, deleteEnabled);
    }

    public record ExternalObject(String name)
    {
        public String getName()
        {
            return name;
        }
    }

    public static final class FakeMcpProvider
    {
        public String getNamespace()
        {
            return "fake";
        }

        public List<Map<String, Object>> getTools()
        {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            schema.put("properties", Map.of("limit", Map.of("type", "integer")));
            Map<String, Object> tool = new LinkedHashMap<>();
            tool.put("name", "items_list");
            tool.put("title", "Items auflisten");
            tool.put("description", "Test-Items auflisten");
            tool.put("inputSchema", schema);
            return List.of(tool);
        }

        public Object call(String localToolName, Map<String, Object> arguments)
        {
            checkEquals("items_list", localToolName, "provider local tool name");
            List<Map<String, Object>> items = List.of(Map.of("name", "eins"), Map.of("name", "zwei"));
            return Map.of("items", items);
        }
    }

    private interface ThrowingRunnable
    {
        void run() throws Exception;
    }

    private static final class FakeAccountProvider implements ReportAccountProvider
    {
        private final ReportTransactionProvider transactionProvider;

        private FakeAccountProvider(ReportTransactionProvider transactionProvider)
        {
            this.transactionProvider = transactionProvider;
        }

        @Override
        public List<ReportAccount> loadAccounts(KontoFilter filter)
        {
            List<ReportAccount> active = List.of(
                account("active", "Aktives Girokonto", "Privat", false),
                account("daily", "Aktives Tagesgeld", "Privat", false));
            if (filter == KontoFilter.ACTIVE)
                return active;
            if (filter == KontoFilter.ALL)
                return List.of(active.get(0), active.get(1), account("archive", "Archivkonto", "Archiv", true));
            throw new AssertionError("unexpected filter: " + filter);
        }

        private ReportAccount account(String id, String name, String group, boolean offline)
        {
            return new ReportAccount(id, 123.45d, 120.00d, LocalDateTime.of(2026, 7, 8, 14, 15, 16),
                name, "12345678", "DE001234", group, !"archive".equals(id), offline,
                ReportTransactionsProxy.forAccount(transactionProvider, id));
        }
    }

    private static final class FakeTransactionProvider implements ReportTransactionProvider
    {
        private final List<ReportTransactionQuery> queries = new ArrayList<>();

        @Override
        public List<ReportTransaction> loadTransactions(ReportTransactionQuery query)
        {
            queries.add(query);
            ReportAccount account = new ReportAccount(query.accountId() == null ? "active" : query.accountId(),
                123.45d, 120.00d, LocalDateTime.of(2026, 7, 8, 14, 15, 16), "Aktives Girokonto",
                "12345678", "DE001234", "Privat", false, null);
            List<ReportTransaction> result = List.of(
                transaction(account, "13", "Food", List.of(new CategoryInfo("13", "Food", false, 0x123456))),
                transaction(account, "14", "Restaurant", List.of(new CategoryInfo("13", "Food", false, 0x123456),
                    new CategoryInfo("14", "Restaurant", false, null))),
                transaction(account, "20", "Auto", List.of(new CategoryInfo("20", "Auto", false, null))));
            result = result.stream().filter(transaction -> matchesCategory(transaction, query)).toList();
            if (query.limit() != null)
                result = result.stream().limit(query.limit()).toList();
            return result;
        }

        private static ReportTransaction transaction(ReportAccount account, String id, String name,
                                                     List<CategoryInfo> path)
        {
            return new ReportTransaction(id, LocalDate.of(2026, 1, 8), LocalDate.of(2026, 1, 8),
                12.34d, 123.45d, "Testumsatz " + id, "", List.of("Testumsatz " + id), "Gegenkonto", "111",
                "222", "Ueberweisung", name, path, false, account);
        }

        private static boolean matchesCategory(ReportTransaction transaction, ReportTransactionQuery query)
        {
            if (query.categoryIds().isEmpty())
                return true;
            List<CategoryInfo> path = transaction.getKategoriePfad();
            if (query.includeSubcategories())
                return path.stream().anyMatch(category -> query.categoryIds().contains(category.id()));
            return !path.isEmpty() && query.categoryIds().contains(path.get(path.size() - 1).id());
        }
    }

    private static final class FakeCategoryProvider implements ReportCategoryProvider
    {
        @Override
        public List<List<CategoryInfo>> loadCategoryPaths()
        {
            return List.of(
                List.of(new CategoryInfo("13", "Food", false, 0x123456)),
                List.of(new CategoryInfo("13", "Food", false, 0x123456),
                    new CategoryInfo("14", "Restaurant", "20", "Nur Restaurant", "restaurant.*", true,
                        "ausgabe", 0, null, null, null, false, true, 0x654321)),
                List.of(new CategoryInfo("99", "Ausgeschlossen", true, null)));
        }
    }

    private static final class FakeAccountSynchronizer implements McpAccountSynchronizer
    {
        private List<ReportAccount> accounts = List.of();
        private boolean all;

        @Override
        public Map<String, Object> sync(List<ReportAccount> accounts, boolean all)
        {
            this.accounts = List.copyOf(accounts);
            this.all = all;
            return Map.of("gestartet", true, "all", all, "count", accounts.size());
        }

        private List<String> accountIds()
        {
            return accounts.stream().map(ReportAccount::getId).toList();
        }
    }

    private static final class FakeTransferDraftWriter implements SepaTransferDraftWriter
    {
        private Request request;

        @Override
        public Result create(Request request)
        {
            this.request = request;
            return new Result("draft-1", request.accountId(), "Aktives Girokonto", request.recipientName(),
                request.recipientIban(), request.recipientBic(), request.amount(), request.executionDate(),
                request.type(), true);
        }
    }

    private static final class FakeTransactionDeleter implements TransactionDeleter
    {
        private final Map<String, Candidate> existing = new LinkedHashMap<>();
        private final Set<String> failingLookups = new HashSet<>();
        private final Set<String> failingDeletes = new HashSet<>();
        private Boolean answer;
        private boolean silentFailure;
        private final List<String> lookups = new ArrayList<>();
        private final List<List<Candidate>> confirmCalls = new ArrayList<>();
        private final List<Integer> unknownCounts = new ArrayList<>();
        private final List<Instant> requestStarts = new ArrayList<>();
        private final List<String> deleted = new ArrayList<>();
        private int refreshCalls;
        private boolean failingRefresh;

        @Override
        public Optional<Candidate> find(String id)
        {
            lookups.add(id);
            if (failingLookups.contains(id))
                throw new IllegalStateException("Suche fehlgeschlagen " + id);
            return Optional.ofNullable(existing.get(id));
        }

        @Override
        public boolean confirm(List<Candidate> candidates, int skippedCount, Instant requestStart)
        {
            confirmCalls.add(List.copyOf(candidates));
            unknownCounts.add(skippedCount);
            requestStarts.add(requestStart);
            if (answer == null)
                throw silentFailure ? new IllegalStateException() : new IllegalStateException("kein GUI");
            return answer;
        }

        @Override
        public void delete(String id)
        {
            if (failingDeletes.contains(id))
                throw new IllegalStateException("Loeschen fehlgeschlagen " + id);
            deleted.add(id);
        }

        @Override
        public void refreshOpenViews()
        {
            refreshCalls++;
            if (failingRefresh)
                throw new IllegalStateException("Ansicht nicht neu geladen");
        }
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
