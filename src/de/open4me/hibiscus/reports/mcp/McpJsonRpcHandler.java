package de.open4me.hibiscus.reports.mcp;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hubspot.jinjava.Jinjava;
import com.hubspot.jinjava.interpret.RenderResult;
import com.hubspot.jinjava.interpret.TemplateError;

import de.open4me.hibiscus.reports.api.ReportTemplateContext;
import de.open4me.hibiscus.reports.data.HibiscusReportCategoryProvider;
import de.open4me.hibiscus.reports.data.ReportCategoryProvider;
import de.open4me.hibiscus.reports.data.ReportAccountGroupsProxy;
import de.open4me.hibiscus.reports.data.ReportAccountsProxy;
import de.open4me.hibiscus.reports.data.ReportTemplateContextFactory;
import de.open4me.hibiscus.reports.data.ReportTransactionProvider;
import de.open4me.hibiscus.reports.data.ReportTransactionsProxy;
import de.open4me.hibiscus.reports.model.CategoryInfo;
import de.open4me.hibiscus.reports.model.ReportAccount;
import de.open4me.hibiscus.reports.model.ReportAccountGroup;
import de.open4me.hibiscus.reports.model.ReportTransaction;

public final class McpJsonRpcHandler
{
    static final String PROTOCOL_VERSION = "2025-11-25";

    private final ObjectMapper mapper = new ObjectMapper();
    private final ReportTemplateContextFactory contextFactory;
    private final ReportTransactionProvider transactionProvider;
    private final ReportCategoryProvider categoryProvider;
    private final SepaTransferDraftWriter transferDraftWriter;
    private final McpAccountSynchronizer accountSynchronizer;
    private final BooleanSupplier writeEnabled;

    public McpJsonRpcHandler(ReportTemplateContextFactory contextFactory,
                             ReportTransactionProvider transactionProvider)
    {
        this(contextFactory, transactionProvider, new HibiscusReportCategoryProvider());
    }

    public McpJsonRpcHandler(ReportTemplateContextFactory contextFactory,
                             ReportTransactionProvider transactionProvider,
                             ReportCategoryProvider categoryProvider)
    {
        this(contextFactory, transactionProvider, categoryProvider, new HibiscusSepaTransferDraftWriter(),
            McpSettings::isWriteEnabled);
    }

    McpJsonRpcHandler(ReportTemplateContextFactory contextFactory,
                      ReportTransactionProvider transactionProvider,
                      ReportCategoryProvider categoryProvider,
                      SepaTransferDraftWriter transferDraftWriter,
                      BooleanSupplier writeEnabled)
    {
        this(contextFactory, transactionProvider, categoryProvider, transferDraftWriter,
            new HibiscusMcpAccountSynchronizer(),
            writeEnabled);
    }

    McpJsonRpcHandler(ReportTemplateContextFactory contextFactory,
                      ReportTransactionProvider transactionProvider,
                      ReportCategoryProvider categoryProvider,
                      SepaTransferDraftWriter transferDraftWriter,
                      McpAccountSynchronizer accountSynchronizer,
                      BooleanSupplier writeEnabled)
    {
        this.contextFactory = contextFactory;
        this.transactionProvider = transactionProvider;
        this.categoryProvider = categoryProvider;
        this.transferDraftWriter = transferDraftWriter;
        this.accountSynchronizer = accountSynchronizer;
        this.writeEnabled = writeEnabled;
    }

    public String handle(String json)
    {
        try
        {
            JsonNode request = mapper.readTree(json);
            if (request.isArray())
            {
                ArrayNode responses = mapper.createArrayNode();
                for (JsonNode item : request)
                {
                    ObjectNode response = handleRequest(item);
                    if (response != null)
                        responses.add(response);
                }
                return responses.isEmpty() ? null : mapper.writeValueAsString(responses);
            }
            ObjectNode response = handleRequest(request);
            return response == null ? null : mapper.writeValueAsString(response);
        }
        catch (Exception e)
        {
            try
            {
                return mapper.writeValueAsString(error(null, -32700, "Parse error", e.getMessage()));
            }
            catch (Exception ignored)
            {
                return "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32700,\"message\":\"Parse error\"},\"id\":null}";
            }
        }
    }

    private ObjectNode handleRequest(JsonNode request)
    {
        JsonNode id = request.get("id");
        String method = text(request.get("method"));
        if (method == null || method.isBlank())
            return error(id, -32600, "Invalid Request", "method is missing");
        if (id == null && method.startsWith("notifications/"))
            return null;

        try
        {
            return switch (method)
            {
                case "initialize" -> success(id, initialize());
                case "tools/list" -> success(id, toolsList());
                case "tools/call" -> success(id, toolsCall(request.path("params")));
                case "resources/list" -> success(id, resourcesList());
                case "resources/read" -> success(id, resourcesRead(request.path("params")));
                default -> error(id, -32601, "Method not found", method);
            };
        }
        catch (Exception e)
        {
            return error(id, -32603, "Internal error", e.getMessage());
        }
    }

    private ObjectNode initialize()
    {
        ObjectNode result = mapper.createObjectNode();
        result.put("protocolVersion", PROTOCOL_VERSION);
        ObjectNode capabilities = result.putObject("capabilities");
        capabilities.putObject("tools");
        capabilities.putObject("resources");
        ObjectNode serverInfo = result.putObject("serverInfo");
        serverInfo.put("name", "hibiscus.ly.reports");
        serverInfo.put("version", "0.7.5");
        return result;
    }

    private ObjectNode toolsList()
    {
        ObjectNode result = mapper.createObjectNode();
        ArrayNode tools = result.putArray("tools");
        tools.add(tool("hibiscus_template_objects_list", "Template-Objekte auflisten",
            "Listet alle Top-Level-Objekte des Report-Template-Kontexts.",
            schema()));
        tools.add(tool("hibiscus_template_render", "Template rendern",
            "Rendert einen Jinjava-Template-String gegen den aktuellen Template-Kontext.",
            templateRenderSchema()));
        tools.add(tool("hibiscus_accounts_list", "Konten auflisten",
            "Listet aktive oder alle Hibiscus-Konten.",
            scopedListSchema("Konten-Umfang: 'active' fuer aktive Konten (Standard) oder 'all' fuer alle Konten.")));
        tools.add(tool("hibiscus_accounts_sync", "Konten synchronisieren",
            "Synchronisiert Hibiscus-Konten ueber die registrierten Synchronisierungs-Backends.",
            syncSchema()));
        tools.add(tool("hibiscus_account_groups_list", "Kontogruppen auflisten",
            "Listet aktive oder alle Kontogruppen, optional inklusive Konten.",
            accountGroupsSchema()));
        tools.add(tool("hibiscus_categories_list", "Kategorien auflisten",
            "Listet alle Hibiscus-Umsatzkategorien mit IDs und Hierarchiepfaden.",
            categoriesSchema()));
        tools.add(tool("hibiscus_transactions_list", "Umsaetze auflisten",
            "Listet Umsaetze mit Zeitraum-, Konto- und Limit-Filtern.",
            transactionsSchema()));
        tools.add(tool("hibiscus_sepa_transfer_create", "SEPA-Ueberweisungsentwurf anlegen",
            "Legt einen lokalen SEPA-Ueberweisungsentwurf in Hibiscus an. Der Auftrag wird nicht an die Bank gesendet.",
            sepaTransferSchema()));
        ReportMcpToolProviders.Loaded providers = ReportMcpToolProviders.load();
        for (ReportMcpToolProviders.Provider provider : providers.providers())
        {
            try
            {
                for (Map<String, Object> definition : provider.tools())
                    tools.add(providerTool(provider, definition));
            }
            catch (Exception e)
            {
                // Provider-Fehler werden in der Resource hibiscus-reports://mcp/providers sichtbar.
            }
        }
        return result;
    }

    private ObjectNode toolsCall(JsonNode params)
    {
        String name = text(params.get("name"));
        JsonNode arguments = params.path("arguments");
        return switch (name)
        {
            case "hibiscus_template_objects_list" -> toolResult(templateObjects(), "Template-Objekte geladen.");
            case "hibiscus_template_render" -> toolResult(renderTemplate(text(arguments.get("template"))),
                "Template gerendert.");
            case "hibiscus_accounts_list" -> toolResult(accounts(arguments), "Konten geladen.");
            case "hibiscus_accounts_sync" -> toolResult(syncAccounts(arguments), "Kontensynchronisierung beendet.");
            case "hibiscus_account_groups_list" -> toolResult(accountGroups(arguments), "Kontogruppen geladen.");
            case "hibiscus_categories_list" -> toolResult(categories(arguments), "Kategorien geladen.");
            case "hibiscus_transactions_list" -> toolResult(transactions(arguments), "Umsaetze geladen.");
            case "hibiscus_sepa_transfer_create" -> toolResult(createSepaTransferDraft(arguments),
                "SEPA-Ueberweisungsentwurf angelegt.");
            default -> providerToolCall(name, arguments);
        };
    }

    private ObjectNode providerToolCall(String name, JsonNode arguments)
    {
        ReportMcpToolProviders.Loaded providers = ReportMcpToolProviders.load();
        ReportMcpToolProviders.Provider provider = providers.find(name);
        if (provider == null)
            throw new IllegalArgumentException("Unbekanntes Tool: " + name);
        try
        {
            JsonNode structured = structured(provider.call(name, arguments(arguments)));
            return toolResult(structured, "Tool " + name + " ausgefuehrt.");
        }
        catch (Exception e)
        {
            throw new IllegalStateException("Provider-Tool konnte nicht ausgefuehrt werden: " + e.getMessage(), e);
        }
    }

    private ObjectNode templateObjects()
    {
        List<String> errors = new ArrayList<>();
        ReportTemplateContext context = contextFactory.create(errors);
        ObjectNode result = mapper.createObjectNode();
        ArrayNode objects = result.putArray("objects");
        for (Map.Entry<String, Object> entry : context.objects().entrySet())
        {
            ObjectNode object = objects.addObject();
            object.put("name", entry.getKey());
            object.put("type", entry.getValue() == null ? "null" : entry.getValue().getClass().getName());
            object.put("iterable", entry.getValue() instanceof Iterable);
        }
        addErrors(result, errors);
        return result;
    }

    private ObjectNode renderTemplate(String template)
    {
        List<String> errors = new ArrayList<>();
        ReportTemplateContext context = contextFactory.create(errors);
        RenderResult rendered = new Jinjava().renderForResult(template == null ? "" : template, context.objects());
        errors.addAll(rendered.getErrors().stream().map(McpJsonRpcHandler::format).toList());
        ObjectNode result = mapper.createObjectNode();
        result.put("html", rendered.getOutput() == null ? "" : rendered.getOutput());
        addErrors(result, errors);
        return result;
    }

    private ObjectNode accounts(JsonNode arguments)
    {
        ReportAccountsProxy proxy = (ReportAccountsProxy) contextFactory.create(new ArrayList<>()).objects().get("konten");
        List<ReportAccount> accounts = "all".equalsIgnoreCase(text(arguments.get("scope")))
            ? proxy.getAlle() : proxy.getAktive();
        ObjectNode result = mapper.createObjectNode();
        ArrayNode array = result.putArray("accounts");
        accounts.forEach(account -> array.add(account(account, false)));
        result.put("count", accounts.size());
        return result;
    }

    private ObjectNode syncAccounts(JsonNode arguments)
    {
        boolean all = bool(arguments.get("all"), false);
        List<ReportAccount> selectedAccounts = all ? List.of() : selectedAccounts(arguments);
        if (!all && selectedAccounts.isEmpty())
            throw new IllegalArgumentException("Keine Konten fuer die Synchronisierung ausgewaehlt.");
        try
        {
            return (ObjectNode) structured(accountSynchronizer.sync(selectedAccounts, all));
        }
        catch (Exception e)
        {
            throw new IllegalStateException("Kontensynchronisierung fehlgeschlagen: " + e.getMessage(), e);
        }
    }

    private List<ReportAccount> selectedAccounts(JsonNode arguments)
    {
        ReportAccountsProxy proxy = (ReportAccountsProxy) contextFactory.create(new ArrayList<>()).objects().get("konten");
        Map<String, ReportAccount> selected = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();

        for (String value : strings(arguments, "accountId", "accountIds"))
            add(selected, missing, value, accountBy(value, proxy.getAlle(), ReportAccount::getId), "accountId");
        for (String value : strings(arguments, "iban", "ibans"))
            add(selected, missing, value, proxy.mitIban(value), "iban");
        for (String value : strings(arguments, "kundennummer", "kundennummern"))
            add(selected, missing, value, proxy.mitKundennummer(value), "kundennummer");
        for (String value : strings(arguments, "kundenkennung", "kundenkennungen"))
            add(selected, missing, value, proxy.mitKundenkennung(value), "kundenkennung");
        for (String value : strings(arguments, "kontonummer", "kontonummern"))
            add(selected, missing, value, proxy.mitKontonummer(value), "kontonummer");
        for (String value : strings(arguments, "bezeichnung", "bezeichnungen"))
            add(selected, missing, value, proxy.mitBezeichnung(value), "bezeichnung");
        for (String value : strings(arguments, "backendClass", "backendClasses"))
        {
            int before = selected.size();
            for (ReportAccount account : proxy.getAlle())
            {
                if (value.equals(account.getBackendClass()))
                    selected.put(account.getId(), account);
            }
            if (selected.size() == before)
                missing.add("backendClass=" + value);
        }

        if (!missing.isEmpty())
            throw new IllegalArgumentException("Konten nicht gefunden: " + String.join(", ", missing));
        return List.copyOf(selected.values());
    }

    private static ReportAccount accountBy(String value, List<ReportAccount> accounts,
                                           Function<ReportAccount, String> getter)
    {
        if (value == null || value.isBlank())
            return null;
        for (ReportAccount account : accounts)
        {
            if (value.equals(getter.apply(account)))
                return account;
        }
        return null;
    }

    private static void add(Map<String, ReportAccount> accounts, List<String> missing, String value,
                            ReportAccount account, String label)
    {
        if (value == null || value.isBlank())
            return;
        if (account == null)
        {
            missing.add(label + "=" + value);
            return;
        }
        accounts.put(account.getId(), account);
    }

    private static List<String> strings(JsonNode arguments, String singular, String plural)
    {
        List<String> result = new ArrayList<>();
        addString(result, arguments.get(singular));
        JsonNode values = arguments.get(plural);
        if (values != null && values.isArray())
        {
            for (JsonNode value : values)
                addString(result, value);
        }
        else
        {
            addString(result, values);
        }
        return result.stream().distinct().toList();
    }

    private static void addString(List<String> values, JsonNode node)
    {
        String value = text(node);
        if (value != null && !value.isBlank())
            values.add(value.trim());
    }

    private ObjectNode accountGroups(JsonNode arguments)
    {
        ReportAccountGroupsProxy proxy = (ReportAccountGroupsProxy) contextFactory.create(new ArrayList<>())
            .objects().get("kontogruppen");
        boolean includeAccounts = bool(arguments.get("includeAccounts"), false);
        List<ReportAccountGroup> groups = "all".equalsIgnoreCase(text(arguments.get("scope")))
            ? proxy.getAlle() : proxy.getAktive();
        ObjectNode result = mapper.createObjectNode();
        ArrayNode array = result.putArray("groups");
        for (ReportAccountGroup group : groups)
            array.add(group(group, includeAccounts));
        result.put("count", groups.size());
        return result;
    }

    private ObjectNode categories(JsonNode arguments)
    {
        boolean includeSkipped = bool(arguments.get("includeSkipped"), true);
        ObjectNode result = mapper.createObjectNode();
        ArrayNode array = result.putArray("categories");
        try
        {
            for (List<CategoryInfo> path : categoryProvider.loadCategoryPaths())
            {
                if (path.isEmpty())
                    continue;
                CategoryInfo category = path.get(path.size() - 1);
                if (!includeSkipped && path.stream().anyMatch(CategoryInfo::skipReports))
                    continue;
                array.add(category(category, path));
            }
        }
        catch (Exception e)
        {
            throw new IllegalStateException("Kategorien konnten nicht geladen werden: " + e.getMessage(), e);
        }
        result.put("count", array.size());
        return result;
    }

    private ObjectNode transactions(JsonNode arguments)
    {
        String accountId = text(arguments.get("accountId"));
        ReportTransactionsProxy proxy = accountId == null || accountId.isBlank()
            ? new ReportTransactionsProxy(transactionProvider)
            : ReportTransactionsProxy.forAccount(transactionProvider, accountId);
        if (bool(arguments.get("all"), false))
            proxy = proxy.getAlle();
        Integer lastDays = integer(arguments.get("lastDays"));
        if (lastDays != null)
            proxy = proxy.letzteTage(lastDays);
        String from = text(arguments.get("from"));
        String to = text(arguments.get("to"));
        if (from != null && !from.isBlank() && to != null && !to.isBlank())
            proxy = proxy.zeitraum(from, to);
        List<String> categoryIds = strings(arguments, "categoryId", "categoryIds");
        if (!categoryIds.isEmpty())
            proxy = proxy.kategorien(categoryIds, bool(arguments.get("includeSubcategories"), false));
        Integer limit = integer(arguments.get("limit"));
        if (limit != null)
            proxy = proxy.limit(limit);

        List<ReportTransaction> transactions = proxy.asList();
        ObjectNode result = mapper.createObjectNode();
        ArrayNode array = result.putArray("transactions");
        transactions.forEach(transaction -> array.add(transaction(transaction)));
        result.put("count", transactions.size());
        return result;
    }

    private ObjectNode createSepaTransferDraft(JsonNode arguments)
    {
        if (!writeEnabled.getAsBoolean())
            throw new IllegalStateException("Ueberweisungen anlegen ist deaktiviert.");
        if (transferDraftWriter == null)
            throw new IllegalStateException("Kein Writer fuer SEPA-Ueberweisungsentwuerfe verfuegbar.");

        SepaTransferDraftWriter.Result draft;
        try
        {
            draft = transferDraftWriter.create(transferRequest(arguments));
        }
        catch (Exception e)
        {
            throw new IllegalStateException("SEPA-Ueberweisungsentwurf konnte nicht angelegt werden: "
                + e.getMessage(), e);
        }
        ObjectNode result = mapper.createObjectNode();
        result.put("id", draft.id());
        result.put("accountId", draft.accountId());
        result.put("accountName", draft.accountName());
        result.put("recipientName", draft.recipientName());
        result.put("recipientIban", draft.recipientIban());
        result.put("recipientBic", draft.recipientBic());
        result.put("amount", draft.amount());
        if (draft.executionDate() != null)
            result.put("executionDate", draft.executionDate().toString());
        result.put("type", draft.type());
        result.put("created", draft.created());
        result.put("status", "Entwurf angelegt");
        result.put("sent", false);
        result.put("sendStatus", "Nicht an die Bank gesendet");
        return result;
    }

    private ObjectNode resourcesList()
    {
        ObjectNode result = mapper.createObjectNode();
        ArrayNode resources = result.putArray("resources");
        resource(resources, "hibiscus-reports://template-context", "Template-Kontext",
            "Top-Level-Objekte im aktuellen Template-Kontext.", "application/json");
        resource(resources, "hibiscus-reports://report-objects-doc", "Report-Objekte",
            "User-Dokumentation der Report-Objekte.", "text/html");
        resource(resources, "hibiscus-reports://mcp/providers", "MCP-Provider",
            "Registrierte MCP-Tool-Provider anderer Plugins.", "application/json");
        return result;
    }

    private ObjectNode resourcesRead(JsonNode params)
    {
        String uri = text(params.get("uri"));
        ObjectNode result = mapper.createObjectNode();
        ArrayNode contents = result.putArray("contents");
        if ("hibiscus-reports://template-context".equals(uri))
        {
            contents.addObject()
                .put("uri", uri)
                .put("mimeType", "application/json")
                .put("text", templateObjects().toString());
            return result;
        }
        if ("hibiscus-reports://report-objects-doc".equals(uri))
        {
            contents.addObject()
                .put("uri", uri)
                .put("mimeType", "text/html")
                .put("text", readResource("help/de_de/reports-objects.html"));
            return result;
        }
        if ("hibiscus-reports://mcp/providers".equals(uri))
        {
            contents.addObject()
                .put("uri", uri)
                .put("mimeType", "application/json")
                .put("text", providerInfo().toPrettyString());
            return result;
        }
        throw new IllegalArgumentException("Unbekannte Resource: " + uri);
    }

    private ObjectNode providerInfo()
    {
        ReportMcpToolProviders.Loaded providers = ReportMcpToolProviders.load();
        ObjectNode result = mapper.createObjectNode();
        ArrayNode providerArray = result.putArray("providers");
        for (ReportMcpToolProviders.Provider provider : providers.providers())
        {
            ObjectNode item = providerArray.addObject();
            item.put("namespace", provider.namespace());
            ArrayNode tools = item.putArray("tools");
            try
            {
                for (Map<String, Object> definition : provider.tools())
                    tools.add(provider.prefix() + string(definition.get("name"), ""));
            }
            catch (Exception e)
            {
                item.put("error", e.getMessage());
            }
        }
        addErrors(result, providers.errors());
        return result;
    }

    private ObjectNode account(ReportAccount account, boolean shallow)
    {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", account.getId());
        node.put("name", account.getName());
        node.put("blz", account.getBlz());
        node.put("kontonummer", account.getKontonummer());
        node.put("kundennummer", account.getKundennummer());
        node.put("kundenkennung", account.getKundenkennung());
        node.put("bezeichnung", account.getBezeichnung());
        node.put("iban", account.getIban());
        node.put("gruppe", account.getGruppe());
        node.put("backendClass", account.getBackendClass());
        node.put("saldo", account.getSaldo());
        node.put("verfuegbar", account.getVerfuegbar());
        node.put("aktiv", account.getAktiv());
        node.put("offline", account.getOffline());
        if (account.getAktualisiert() != null)
            node.put("aktualisiert", account.getAktualisiert().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        if (!shallow)
            node.put("umsaetzeTool", "hibiscus_transactions_list");
        return node;
    }

    private ObjectNode group(ReportAccountGroup group, boolean includeAccounts)
    {
        ObjectNode node = mapper.createObjectNode();
        node.put("name", group.getName());
        node.put("anzahl", group.getAnzahl());
        node.put("saldo", group.getSaldo());
        node.put("verfuegbar", group.getVerfuegbar());
        if (includeAccounts)
        {
            ArrayNode accounts = node.putArray("konten");
            group.getKonten().forEach(account -> accounts.add(account(account, true)));
        }
        return node;
    }

    private ObjectNode transaction(ReportTransaction transaction)
    {
        ObjectNode node = mapper.createObjectNode();
        if (transaction.getDatum() != null)
            node.put("datum", transaction.getDatum().toString());
        if (transaction.getValuta() != null)
            node.put("valuta", transaction.getValuta().toString());
        node.put("betrag", transaction.getBetrag());
        node.put("saldo", transaction.getSaldo());
        node.put("zweck", transaction.getZweck());
        node.put("zweck2", transaction.getZweck2());
        node.put("gegenkontoName", transaction.getGegenkontoName());
        node.put("gegenkontoNummer", transaction.getGegenkontoNummer());
        node.put("gegenkontoBlz", transaction.getGegenkontoBlz());
        node.put("art", transaction.getArt());
        node.put("kategorie", transaction.getKategorie());
        node.put("vorgemerkt", transaction.isVorgemerkt());
        ArrayNode verwendungszwecke = node.putArray("verwendungszwecke");
        transaction.getVerwendungszwecke().forEach(verwendungszwecke::add);
        ArrayNode kategoriePfad = node.putArray("kategoriePfad");
        transaction.getKategoriePfad().forEach(category -> kategoriePfad.addObject()
            .put("id", category.id())
            .put("name", category.name())
            .put("color", category.color())
            .put("skipReports", category.skipReports()));
        if (transaction.getKonto() != null)
            node.set("konto", account(transaction.getKonto(), true));
        return node;
    }

    private ObjectNode category(CategoryInfo category, List<CategoryInfo> path)
    {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", category.id());
        node.put("name", category.name());
        if (path.size() > 1)
            node.put("parentId", path.get(path.size() - 2).id());
        else
            node.putNull("parentId");
        node.put("level", Math.max(0, path.size() - 1));
        node.put("path", path.stream().map(CategoryInfo::name)
            .reduce((left, right) -> left + " > " + right).orElse(category.name()));
        ArrayNode pathIds = node.putArray("pathIds");
        path.forEach(item -> pathIds.add(item.id()));
        if (category.color() == null)
            node.putNull("color");
        else
            node.put("color", category.color());
        node.put("skipReports", category.skipReports());
        node.put("transactionsTool", "hibiscus_transactions_list");
        return node;
    }

    private ObjectNode toolResult(JsonNode structured, String text)
    {
        ObjectNode result = mapper.createObjectNode();
        result.set("structuredContent", structured);
        ArrayNode content = result.putArray("content");
        content.addObject().put("type", "text").put("text", text + "\n\n" + structured.toPrettyString());
        return result;
    }

    private ObjectNode tool(String name, String title, String description, ObjectNode schema)
    {
        ObjectNode tool = mapper.createObjectNode();
        tool.put("name", name);
        tool.put("title", title);
        tool.put("description", description);
        tool.set("inputSchema", schema);
        return tool;
    }

    private ObjectNode providerTool(ReportMcpToolProviders.Provider provider, Map<String, Object> definition)
    {
        String localName = string(definition.get("name"), null);
        if (localName == null || localName.isBlank())
            throw new IllegalArgumentException("Provider-Tool ohne Namen.");
        ObjectNode tool = mapper.createObjectNode();
        tool.put("name", provider.prefix() + localName);
        tool.put("title", string(definition.get("title"), provider.prefix() + localName));
        tool.put("description", string(definition.get("description"), ""));
        JsonNode schema = structured(definition.get("inputSchema"));
        tool.set("inputSchema", schema.isObject() ? schema : schema());
        return tool;
    }

    private ObjectNode schema()
    {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.putObject("properties");
        return schema;
    }

    private ObjectNode syncSchema()
    {
        ObjectNode schema = schema();
        ObjectNode props = (ObjectNode) schema.get("properties");
        property(props, "all", "boolean", "Alle synchronisierbaren Konten",
            "Wenn true, werden alle synchronisierbaren Konten synchronisiert und Auswahlfelder ignoriert.");
        property(props, "accountId", "string", "Konto-ID",
            "Exakte Hibiscus-Konto-ID eines zu synchronisierenden Kontos.");
        arrayProperty(props, "accountIds", "Konto-IDs",
            "Liste exakter Hibiscus-Konto-IDs.");
        property(props, "iban", "string", "IBAN",
            "IBAN eines zu synchronisierenden Kontos; Leerzeichen sind erlaubt.");
        arrayProperty(props, "ibans", "IBANs",
            "Liste von IBANs; Leerzeichen sind erlaubt.");
        property(props, "kundennummer", "string", "Kundennummer",
            "Kundennummer eines zu synchronisierenden Kontos.");
        arrayProperty(props, "kundennummern", "Kundennummern",
            "Liste von Kundennummern.");
        property(props, "kundenkennung", "string", "Kundenkennung",
            "Kundenkennung eines zu synchronisierenden Kontos.");
        arrayProperty(props, "kundenkennungen", "Kundenkennungen",
            "Liste von Kundenkennungen.");
        property(props, "kontonummer", "string", "Kontonummer",
            "Kontonummer eines zu synchronisierenden Kontos.");
        arrayProperty(props, "kontonummern", "Kontonummern",
            "Liste von Kontonummern.");
        property(props, "bezeichnung", "string", "Bezeichnung",
            "Exakte Kontobezeichnung eines zu synchronisierenden Kontos.");
        arrayProperty(props, "bezeichnungen", "Bezeichnungen",
            "Liste exakter Kontobezeichnungen.");
        property(props, "backendClass", "string", "Backend-Klasse",
            "Vollqualifizierter Backend-Klassenname; synchronisiert passende Konten.");
        arrayProperty(props, "backendClasses", "Backend-Klassen",
            "Liste vollqualifizierter Backend-Klassennamen.");
        return schema;
    }

    private ObjectNode templateRenderSchema()
    {
        ObjectNode schema = schema();
        ObjectNode props = (ObjectNode) schema.get("properties");
        property(props, "template", "string", "Template",
            "Jinjava-Template-String, der gegen den Hibiscus-Report-Kontext gerendert wird.");
        schema.putArray("required").add("template");
        return schema;
    }

    private ObjectNode scopedListSchema(String scopeDescription)
    {
        ObjectNode schema = schema();
        ObjectNode props = (ObjectNode) schema.get("properties");
        enumProperty(props, "scope", "Umfang", scopeDescription, "active", "all");
        return schema;
    }

    private ObjectNode accountGroupsSchema()
    {
        ObjectNode schema = scopedListSchema("Kontogruppen-Umfang: 'active' fuer aktive Kontogruppen "
            + "(Standard) oder 'all' fuer alle Kontogruppen.");
        ObjectNode props = (ObjectNode) schema.get("properties");
        property(props, "includeAccounts", "boolean", "Konten einschliessen",
            "Wenn true, enthaelt jede Kontogruppe eine flache Liste ihrer Konten.");
        return schema;
    }

    private ObjectNode transactionsSchema()
    {
        ObjectNode schema = schema();
        ObjectNode props = (ObjectNode) schema.get("properties");
        property(props, "accountId", "string", "Konto-ID",
            "Optionale Hibiscus-Konto-ID. Wenn nicht gesetzt, werden Umsaetze kontoübergreifend gesucht.");
        property(props, "categoryId", "string", "Kategorie-ID",
            "Optionale Kategorie-ID aus hibiscus_categories_list. Standard: nur direkt auf diese Kategorie gebuchte Umsaetze.");
        arrayProperty(props, "categoryIds", "Kategorie-IDs",
            "Optionale Liste von Kategorie-IDs aus hibiscus_categories_list.");
        property(props, "includeSubcategories", "boolean", "Unterkategorien einschliessen",
            "Wenn true, matchen categoryId/categoryIds auch Umsaetze in Unterkategorien.");
        dateProperty(props, "from", "Von",
            "Startdatum inklusive im ISO-Format YYYY-MM-DD. Wirkt nur zusammen mit 'to'.");
        dateProperty(props, "to", "Bis",
            "Enddatum inklusive im ISO-Format YYYY-MM-DD. Wirkt nur zusammen mit 'from'.");
        property(props, "lastDays", "integer", "Letzte Tage",
            "Relativer Zeitraum von heute rueckwaerts. 0 bedeutet nur heute.");
        property(props, "limit", "integer", "Limit",
            "Maximale Anzahl zurueckgegebener Umsaetze.");
        property(props, "all", "boolean", "Alle Umsaetze",
            "Wenn true, wird der Standardzeitraum der letzten 90 Tage aufgehoben. Zeitraum-Filter koennen danach weiter einschraenken.");
        return schema;
    }

    private ObjectNode categoriesSchema()
    {
        ObjectNode schema = schema();
        ObjectNode props = (ObjectNode) schema.get("properties");
        property(props, "includeSkipped", "boolean", "Ausgeschlossene Kategorien einschliessen",
            "Wenn false, werden Kategorien ausgeblendet, die fuer Reports ausgeschlossen sind oder einen ausgeschlossenen Elternknoten haben. Standard ist true.");
        return schema;
    }

    private ObjectNode sepaTransferSchema()
    {
        ObjectNode schema = schema();
        ObjectNode props = (ObjectNode) schema.get("properties");
        property(props, "accountId", "string", label("accountId"),
            "Hibiscus-Konto-ID des Auftraggeberkontos.");
        property(props, "recipientName", "string", label("recipientName"),
            "Name des Zahlungsempfaengers.");
        property(props, "recipientIban", "string", label("recipientIban"),
            "IBAN des Zahlungsempfaengers; Leerzeichen werden entfernt.");
        property(props, "recipientBic", "string", label("recipientBic"),
            "Optionale BIC des Zahlungsempfaengers; Leerzeichen werden entfernt.");
        property(props, "amount", "number", label("amount"),
            "Positiver Betrag in Euro. Dezimalpunkt oder Dezimalkomma sind erlaubt.");
        property(props, "purpose", "string", label("purpose"),
            "Erste Zeile des Verwendungszwecks.");
        property(props, "purpose2", "string", label("purpose2"),
            "Zweite Zeile des Verwendungszwecks.");
        property(props, "additionalPurposes", "array", label("additionalPurposes"),
            "Weitere Verwendungszweck-Zeilen.")
            .putObject("items").put("type", "string");
        dateProperty(props, "executionDate", label("executionDate"),
            "Ausfuehrungsdatum im ISO-Format YYYY-MM-DD. Wenn nicht gesetzt, wird heute verwendet.");
        property(props, "endToEndId", "string", label("endToEndId"),
            "Optionale SEPA End-to-End-ID.");
        property(props, "pmtInfId", "string", label("pmtInfId"),
            "Optionale SEPA Payment-Information-ID.");
        property(props, "purposeCode", "string", label("purposeCode"),
            "Optionaler SEPA Purpose-Code, z. B. vierstelliger ISO-Code.");
        ObjectNode type = enumProperty(props, "type", label("type"),
            "Optionale Ueberweisungsart. Wenn nicht gesetzt, verwendet Hibiscus den Standard.",
            "Überweisung", "Terminüberweisung", "Interne Umbuchung", "Echtzeitüberweisung");
        ArrayNode required = schema.putArray("required");
        required.add("accountId");
        required.add("recipientName");
        required.add("recipientIban");
        required.add("amount");
        return schema;
    }

    private SepaTransferDraftWriter.Request transferRequest(JsonNode arguments)
    {
        String accountId = requiredText(arguments, "accountId");
        String recipientName = requiredText(arguments, "recipientName");
        String recipientIban = compact(requiredText(arguments, "recipientIban"));
        String recipientBic = compact(optionalText(arguments, "recipientBic"));
        BigDecimal amount = amount(arguments.get("amount"));
        if (amount.signum() <= 0)
            throw new IllegalArgumentException("Der Betrag muss größer als 0 sein.");

        return new SepaTransferDraftWriter.Request(accountId, recipientName, recipientIban, recipientBic,
            amount, optionalText(arguments, "purpose"), optionalText(arguments, "purpose2"),
            stringList(arguments.get("additionalPurposes")), localDate(arguments.get("executionDate")),
            optionalText(arguments, "endToEndId"), optionalText(arguments, "pmtInfId"),
            optionalText(arguments, "purposeCode"), optionalText(arguments, "type"));
    }

    private static String requiredText(JsonNode arguments, String name)
    {
        String value = optionalText(arguments, name);
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("Pflichtfeld fehlt: " + label(name) + ".");
        return value;
    }

    private static String optionalText(JsonNode arguments, String name)
    {
        String value = text(arguments.get(name));
        return value == null ? null : value.trim();
    }

    private static String compact(String value)
    {
        return value == null ? null : value.replaceAll("\\s+", "");
    }

    private static BigDecimal amount(JsonNode node)
    {
        if (node == null || node.isMissingNode() || node.isNull())
            throw new IllegalArgumentException("Pflichtfeld fehlt: " + label("amount") + ".");
        try
        {
            BigDecimal value = new BigDecimal(node.asText().replace(',', '.'));
            if (!Double.isFinite(value.doubleValue()))
                throw new NumberFormatException("nicht endlich");
            return value;
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("Der Betrag ist ungültig.");
        }
    }

    private static LocalDate localDate(JsonNode node)
    {
        String value = text(node);
        if (value == null || value.isBlank())
            return LocalDate.now();
        return LocalDate.parse(value.trim());
    }

    private static List<String> stringList(JsonNode node)
    {
        if (node == null || node.isMissingNode() || node.isNull())
            return List.of();
        if (!node.isArray())
            throw new IllegalArgumentException(label("additionalPurposes") + " muss eine Liste sein.");
        List<String> values = new ArrayList<>();
        for (JsonNode item : node)
        {
            String value = text(item);
            if (value != null && !value.isBlank())
                values.add(value.trim());
        }
        return List.copyOf(values);
    }

    private static ObjectNode property(ObjectNode properties, String name, String type, String title)
    {
        return property(properties, name, type, title, null);
    }

    private static ObjectNode property(ObjectNode properties, String name, String type, String title,
                                       String description)
    {
        ObjectNode property = properties.putObject(name);
        property.put("type", type);
        property.put("title", title);
        if (description != null && !description.isBlank())
            property.put("description", description);
        return property;
    }

    private static ObjectNode arrayProperty(ObjectNode properties, String name, String title)
    {
        return arrayProperty(properties, name, title, null);
    }

    private static ObjectNode arrayProperty(ObjectNode properties, String name, String title,
                                            String description)
    {
        ObjectNode property = property(properties, name, "array", title, description);
        property.putObject("items").put("type", "string");
        return property;
    }

    private static ObjectNode dateProperty(ObjectNode properties, String name, String title,
                                           String description)
    {
        ObjectNode property = property(properties, name, "string", title, description);
        property.put("format", "date");
        property.put("pattern", "^\\d{4}-\\d{2}-\\d{2}$");
        return property;
    }

    private static ObjectNode enumProperty(ObjectNode properties, String name, String title,
                                           String description, String... values)
    {
        ObjectNode property = property(properties, name, "string", title, description);
        ArrayNode enumValues = property.putArray("enum");
        for (String value : values)
            enumValues.add(value);
        return property;
    }

    private static String label(String name)
    {
        return switch (name)
        {
            case "accountId" -> "Auftraggeberkonto";
            case "recipientName" -> "Empfänger";
            case "recipientIban" -> "Empfänger-IBAN";
            case "recipientBic" -> "Empfänger-BIC";
            case "amount" -> "Betrag";
            case "purpose" -> "Verwendungszweck";
            case "purpose2" -> "Verwendungszweck 2";
            case "additionalPurposes" -> "Weitere Verwendungszwecke";
            case "executionDate" -> "Ausführungstermin";
            case "endToEndId" -> "End-to-End-ID";
            case "pmtInfId" -> "Payment-Information-ID";
            case "purposeCode" -> "SEPA Purpose-Code";
            case "type" -> "Überweisungsart";
            default -> name;
        };
    }

    private Map<String, Object> arguments(JsonNode node)
    {
        if (node == null || node.isMissingNode() || node.isNull())
            return Map.of();
        if (!node.isObject())
            throw new IllegalArgumentException("Tool-Argumente muessen ein Objekt sein.");
        Map<String, Object> result = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> result.put(entry.getKey(), value(entry.getValue())));
        return result;
    }

    private Object value(JsonNode node)
    {
        if (node == null || node.isMissingNode() || node.isNull())
            return null;
        if (node.isTextual())
            return node.asText();
        if (node.isBoolean())
            return node.asBoolean();
        if (node.isInt() || node.isLong())
            return node.asLong();
        if (node.isNumber())
            return node.asDouble();
        if (node.isArray())
        {
            List<Object> values = new ArrayList<>();
            node.forEach(item -> values.add(value(item)));
            return values;
        }
        if (node.isObject())
        {
            Map<String, Object> values = new LinkedHashMap<>();
            node.fields().forEachRemaining(entry -> values.put(entry.getKey(), value(entry.getValue())));
            return values;
        }
        return node.asText();
    }

    private JsonNode structured(Object value)
    {
        if (value == null)
            return mapper.nullNode();
        if (value instanceof String text)
            return mapper.getNodeFactory().textNode(text);
        if (value instanceof Boolean bool)
            return mapper.getNodeFactory().booleanNode(bool);
        if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte)
            return mapper.getNodeFactory().numberNode(((Number) value).longValue());
        if (value instanceof Float || value instanceof Double)
            return mapper.getNodeFactory().numberNode(((Number) value).doubleValue());
        if (value instanceof java.math.BigDecimal decimal)
            return mapper.getNodeFactory().numberNode(decimal);
        if (value instanceof LocalDate date)
            return mapper.getNodeFactory().textNode(date.toString());
        if (value instanceof LocalDateTime dateTime)
            return mapper.getNodeFactory().textNode(dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        if (value instanceof Date date)
            return mapper.getNodeFactory().textNode(new SimpleDateFormat("yyyy-MM-dd").format(date));
        if (value instanceof Map<?, ?> map)
        {
            ObjectNode object = mapper.createObjectNode();
            for (Map.Entry<?, ?> entry : map.entrySet())
                object.set(String.valueOf(entry.getKey()), structured(entry.getValue()));
            return object;
        }
        if (value instanceof Iterable<?> iterable)
        {
            ArrayNode array = mapper.createArrayNode();
            iterable.forEach(item -> array.add(structured(item)));
            return array;
        }
        throw new IllegalArgumentException("Nicht serialisierbarer MCP-Wert: " + value.getClass().getName());
    }

    private static void resource(ArrayNode resources, String uri, String name, String description, String mimeType)
    {
        resources.addObject()
            .put("uri", uri)
            .put("name", name)
            .put("description", description)
            .put("mimeType", mimeType);
    }

    private static String string(Object value, String fallback)
    {
        return value == null ? fallback : String.valueOf(value);
    }

    private ObjectNode success(JsonNode id, JsonNode result)
    {
        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("result", result);
        response.set("id", id == null ? mapper.nullNode() : id);
        return response;
    }

    private ObjectNode error(JsonNode id, int code, String message, String data)
    {
        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        ObjectNode error = response.putObject("error");
        error.put("code", code);
        error.put("message", message);
        if (data != null && !data.isBlank())
            error.put("data", data);
        response.set("id", id == null ? mapper.nullNode() : id);
        return response;
    }

    private static void addErrors(ObjectNode result, List<String> errors)
    {
        ArrayNode array = result.putArray("errors");
        errors.forEach(array::add);
    }

    private static String format(TemplateError error)
    {
        return error == null ? "" : error.toString();
    }

    private static String text(JsonNode node)
    {
        if (node == null || node.isMissingNode() || node.isNull())
            return null;
        return node.asText();
    }

    private static Integer integer(JsonNode node)
    {
        if (node == null || node.isMissingNode() || node.isNull())
            return null;
        return node.asInt();
    }

    private static boolean bool(JsonNode node, boolean fallback)
    {
        if (node == null || node.isMissingNode() || node.isNull())
            return fallback;
        return node.asBoolean(fallback);
    }

    private static String readResource(String name)
    {
        try (InputStream input = McpJsonRpcHandler.class.getClassLoader().getResourceAsStream(name))
        {
            if (input == null)
                return "";
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (Exception e)
        {
            return "";
        }
    }
}
