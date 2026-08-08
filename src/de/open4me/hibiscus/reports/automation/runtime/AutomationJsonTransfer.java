package de.open4me.hibiscus.reports.automation.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.open4me.hibiscus.reports.automation.model.Automation;
import de.open4me.hibiscus.reports.automation.model.AutomationTrigger;
import de.open4me.hibiscus.reports.automation.model.AutomationTriggerTypes;
import de.open4me.hibiscus.reports.automation.model.MissedTriggerPolicy;
import de.open4me.hibiscus.reports.automation.model.RunMode;
import de.open4me.hibiscus.reports.automation.sql.AutomationRepository;

public final class AutomationJsonTransfer
{
    private static final String FORMAT = "hibiscus.ly.reports.automation";
    private static final int VERSION = 1;

    private final AutomationRepository repository;
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final AutomationSchedule schedule = new AutomationSchedule();

    public AutomationJsonTransfer(AutomationRepository repository)
    {
        this.repository = repository;
    }

    public void exportAutomation(Automation automation, Path target) throws Exception
    {
        if (automation == null || automation.id() == null || automation.id().isBlank())
            throw new IllegalArgumentException("Bitte zuerst eine gespeicherte Automation auswaehlen.");
        ObjectNode root = mapper.createObjectNode();
        root.put("format", FORMAT);
        root.put("version", VERSION);
        root.set("automation", automation(automation));
        ArrayNode triggers = root.putArray("triggers");
        for (AutomationTrigger trigger : repository.listTriggers(automation.id()))
            triggers.add(trigger(trigger));
        Files.writeString(target, mapper.writeValueAsString(root), StandardCharsets.UTF_8);
    }

    public Automation importAutomation(Path source) throws Exception
    {
        JsonNode root = mapper.readTree(Files.readString(source, StandardCharsets.UTF_8));
        validate(root);
        JsonNode automationNode = root.path("automation");
        String name = uniqueName(text(automationNode, "name"));
        Automation saved = repository.saveAutomation(new Automation(null, name,
            text(automationNode, "description"),
            bool(automationNode, "active", true),
            RunMode.parse(text(automationNode, "mode")),
            MissedTriggerPolicy.parse(text(automationNode, "missedTriggerPolicy")),
            text(automationNode, "script"),
            integer(automationNode, "historyLimit", 100)));

        for (JsonNode triggerNode : root.path("triggers"))
        {
            String type = text(triggerNode, "type");
            if (type.isBlank())
                type = AutomationTriggerTypes.CRON;
            String expression = text(triggerNode, "schedule");
            LocalDateTime nextRun = AutomationTriggerTypes.CRON.equals(type) && !expression.isBlank()
                ? schedule.next(expression, LocalDateTime.now()) : null;
            repository.saveTrigger(new AutomationTrigger(null, saved.id(),
                text(triggerNode, "name"),
                bool(triggerNode, "active", false),
                type,
                expression,
                nextRun,
                null));
        }
        return saved;
    }

    private void validate(JsonNode root) throws IOException
    {
        if (!FORMAT.equals(text(root, "format")))
            throw new IOException("Die Datei ist kein Automation-Export.");
        if (integer(root, "version", 0) != VERSION)
            throw new IOException("Automation-Export-Version wird nicht unterstuetzt.");
        if (!root.path("automation").isObject())
            throw new IOException("Automation-Export enthaelt keine Automation.");
        JsonNode triggers = root.path("triggers");
        if (!triggers.isMissingNode() && !triggers.isArray())
            throw new IOException("Automation-Export enthaelt ungueltige Trigger.");
    }

    private String uniqueName(String importedName) throws Exception
    {
        String base = importedName.isBlank() ? "Importierte Automation" : importedName;
        List<String> names = new ArrayList<>();
        for (Automation automation : repository.listAutomations())
            names.add(automation.name());
        if (!names.contains(base))
            return base;
        String importName = base + " Import";
        if (!names.contains(importName))
            return importName;
        int index = 2;
        while (names.contains(importName + " " + index))
            index++;
        return importName + " " + index;
    }

    private ObjectNode automation(Automation automation)
    {
        ObjectNode node = mapper.createObjectNode();
        node.put("name", automation.name());
        node.put("description", automation.description());
        node.put("active", automation.active());
        node.put("mode", automation.mode().value());
        node.put("missedTriggerPolicy", automation.missedTriggerPolicy().value());
        node.put("script", automation.script());
        node.put("historyLimit", automation.historyLimit());
        return node;
    }

    private ObjectNode trigger(AutomationTrigger trigger)
    {
        ObjectNode node = mapper.createObjectNode();
        node.put("name", trigger.name());
        node.put("active", trigger.active());
        node.put("type", trigger.type());
        node.put("schedule", trigger.schedule());
        return node;
    }

    private static String text(JsonNode node, String name)
    {
        JsonNode value = node.path(name);
        return value.isTextual() ? value.asText() : "";
    }

    private static boolean bool(JsonNode node, String name, boolean fallback)
    {
        JsonNode value = node.path(name);
        return value.isBoolean() ? value.asBoolean() : fallback;
    }

    private static int integer(JsonNode node, String name, int fallback)
    {
        JsonNode value = node.path(name);
        return value.isInt() ? value.asInt() : fallback;
    }
}
