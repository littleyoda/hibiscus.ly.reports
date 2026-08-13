package de.open4me.hibiscus.reports.automation.runtime;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.openjdk.nashorn.api.scripting.ScriptObjectMirror;

import de.open4me.hibiscus.reports.mcp.HibiscusSepaTransferDraftWriter;
import de.open4me.hibiscus.reports.mcp.SepaTransferDraftWriter;
import de.open4me.hibiscus.reports.model.ReportAccount;

public final class AutomationPayments
{
    private final AutomationContext context;
    private final AutomationDialogs dialogs;
    private final AutomationLogger log;
    private final SepaTransferDraftWriter writer;

    public AutomationPayments(AutomationContext context, AutomationDialogs dialogs, AutomationLogger log)
    {
        this(context, dialogs, log, new HibiscusSepaTransferDraftWriter());
    }

    AutomationPayments(AutomationContext context, AutomationDialogs dialogs, AutomationLogger log,
                       SepaTransferDraftWriter writer)
    {
        this.context = context;
        this.dialogs = dialogs;
        this.log = log;
        this.writer = writer;
    }

    public Object entwurf(Object value) throws Exception
    {
        RequestData data = requestData(value);
        validate(data);

        String summary = summary(data);

        if (context.getTestlauf() || !context.getWriteAllowed())
        {
            log.info("Testlauf: Zahlungsentwurf geplant:\n" + summary);
            return Map.of("created", false, "testlauf", true, "accountId", data.account.getId());
        }

        if (!dialogs.bestaetigen("Zahlungsentwurf bestaetigen", summary))
            throw new AutomationCanceledException("Zahlungsentwurf wurde abgebrochen.");

        SepaTransferDraftWriter.Result result = writer.create(new SepaTransferDraftWriter.Request(
            data.account.getId(), data.recipientName, data.iban, data.bic, data.amount, data.purpose,
            data.purpose2, data.additionalPurposes, data.executionDate, null, null, null, data.art));
        log.info("Zahlungsentwurf angelegt: " + result.id());
        return result;
    }

    static String summary(Object value)
    {
        RequestData data = requestData(value);
        validate(data);
        return summary(data);
    }

    private static RequestData requestData(Object value)
    {
        Map<String, Object> map = asMap(value);
        ReportAccount account = account(map.get("konto"));
        return new RequestData(account, text(map.get("art")), text(map.get("empfaengerName")),
            text(map.get("iban")), text(map.get("bic")), amount(map.get("betrag")),
            text(map.get("verwendungszweck")), text(map.get("verwendungszweck2")),
            list(map.get("weitereVerwendungszwecke")), date(map.get("termin")), dialogText(map));
    }

    private static String summary(RequestData data)
    {
        StringBuilder summary = new StringBuilder(data.art);
        if (!data.dialogText.isBlank())
            summary.append("\n\n").append(data.dialogText);
        summary.append("\n\nKonto: ").append(data.account.getName())
            .append("\nEmpfaenger: ").append(data.recipientName)
            .append("\nIBAN: ").append(data.iban)
            .append("\nBIC: ").append(data.bic)
            .append("\nBetrag: ").append(data.amount)
            .append("\nTermin: ").append(data.executionDate)
            .append("\nZweck: ").append(data.purpose);
        return summary.toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value)
    {
        if (value instanceof ScriptObjectMirror mirror)
            return (Map<String, Object>) mirror;
        if (value instanceof Map<?, ?> map)
            return (Map<String, Object>) map;
        throw new IllegalArgumentException("zahlungen.entwurf erwartet ein Objekt.");
    }

    private static ReportAccount account(Object value)
    {
        if (value instanceof ReportAccount account)
            return account;
        throw new IllegalArgumentException("zahlungen.entwurf erwartet konto als Kontoobjekt.");
    }

    private static BigDecimal amount(Object value)
    {
        if (value instanceof BigDecimal decimal)
            return decimal;
        if (value instanceof Number number)
            return BigDecimal.valueOf(number.doubleValue());
        if (value != null && !value.toString().isBlank())
            return new BigDecimal(value.toString());
        return null;
    }

    private static LocalDate date(Object value)
    {
        if (value == null || value.toString().isBlank())
            return null;
        if (value instanceof LocalDate localDate)
            return localDate;
        return LocalDate.parse(value.toString());
    }

    private static List<String> list(Object value)
    {
        if (value == null)
            return List.of();
        if (value instanceof List<?> items)
            return items.stream().map(String::valueOf).toList();
        if (value instanceof ScriptObjectMirror mirror && mirror.isArray())
        {
            List<String> result = new ArrayList<>();
            for (Object item : mirror.values())
                result.add(String.valueOf(item));
            return result;
        }
        return List.of(String.valueOf(value));
    }

    private static String text(Object value)
    {
        return value == null ? "" : value.toString();
    }

    private static String dialogText(Map<String, Object> map)
    {
        String value = text(map.get("dialogText"));
        if (value.isBlank())
            value = text(map.get("dialogtext"));
        return value.trim();
    }

    private static void validate(RequestData data)
    {
        if (data.account == null)
            throw new IllegalArgumentException("Auftraggeberkonto fehlt.");
        if (data.account.getOffline())
            throw new IllegalArgumentException("Offline-Konten koennen keine Zahlungsentwuerfe anlegen.");
        if (data.recipientName.isBlank())
            throw new IllegalArgumentException("Empfaengername fehlt.");
        if (data.iban.isBlank())
            throw new IllegalArgumentException("Empfaenger-IBAN fehlt.");
        if (data.amount == null)
            throw new IllegalArgumentException("Betrag fehlt.");
    }

    private record RequestData(ReportAccount account, String art, String recipientName, String iban, String bic,
                               BigDecimal amount, String purpose, String purpose2, List<String> additionalPurposes,
                               LocalDate executionDate, String dialogText)
    {
        RequestData
        {
            art = art == null || art.isBlank() ? "Überweisung" : art;
            purpose = purpose == null ? "" : purpose;
            purpose2 = purpose2 == null ? "" : purpose2;
            additionalPurposes = additionalPurposes == null ? List.of() : List.copyOf(additionalPurposes);
            dialogText = dialogText == null ? "" : dialogText;
        }
    }
}
