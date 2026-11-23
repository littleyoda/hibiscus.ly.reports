package de.open4me.hibiscus.reports.automation.runtime;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.openjdk.nashorn.api.scripting.ScriptObjectMirror;

import de.open4me.hibiscus.reports.mcp.HibiscusSepaTransferDraftWriter;
import de.open4me.hibiscus.reports.mcp.SepaPurpose;
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
        Draft draft = draft(value);

        if (context.getTestlauf() || !context.getWriteAllowed())
        {
            log.info("Testlauf: Zahlungsentwurf geplant:\n" + draft.summary());
            return Map.of("created", false, "testlauf", true, "accountId", draft.request().accountId());
        }

        if (!dialogs.bestaetigen("Zahlungsentwurf bestaetigen", draft.summary()))
            throw new AutomationCanceledException("Zahlungsentwurf wurde abgebrochen.");

        SepaTransferDraftWriter.Result result = writer.create(draft.request());
        log.info("Zahlungsentwurf angelegt: " + result.id());
        return result;
    }

    static String summary(Object value)
    {
        return draft(value).summary();
    }

    /**
     * Fuehrt den Verwendungszweck einmal zusammen und prueft ihn wie Hibiscus (Laenge und Zeichen). Bestaetigung,
     * Testlauf-Log und Writer bekommen denselben Text; Fehler fallen vor der Bestaetigung auf.
     */
    static Draft draft(Object value)
    {
        RequestData data = requestData(value);
        validate(data);
        String purpose = SepaPurpose.mergeForHibiscus(data.purpose, data.purpose2, data.additionalPurposes);
        SepaTransferDraftWriter.Request request = new SepaTransferDraftWriter.Request(data.account.getId(),
            data.recipientName, data.iban, data.bic, data.amount, purpose, null, List.of(), data.executionDate,
            null, null, null, data.art);
        return new Draft(summary(data, purpose), request);
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

    private static String summary(RequestData data, String purpose)
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
            .append("\nZweck: ").append(purpose == null ? "" : purpose);
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

    /** Liste von Texten; {@code null}- und {@code undefined}-Eintraege entfallen. */
    private static List<String> list(Object value)
    {
        if (missing(value))
            return List.of();
        Iterable<?> items;
        if (value instanceof ScriptObjectMirror mirror && mirror.isArray())
            items = mirror.values();
        else if (value instanceof List<?> list)
            items = list;
        else
            items = List.of(value);
        List<String> result = new ArrayList<>();
        for (Object item : items)
        {
            if (!missing(item))
                result.add(text(item));
        }
        return result;
    }

    /** Text wie in JavaScript: {@code null}/{@code undefined} werden leer, 4711.0 wird "4711". */
    private static String text(Object value)
    {
        if (missing(value))
            return "";
        if (value instanceof Double || value instanceof Float)
            return javaScriptNumber(((Number) value).doubleValue());
        return value.toString();
    }

    /**
     * Zahl wie JavaScripts {@code String(x)} (ECMAScript Number::toString): ohne Exponent fuer 1e-6 <= |x| < 1e21,
     * sonst mit ({@code 1e+21}, {@code 1.5e-7}). Die Ziffern sind die kuerzesten, die wieder genau diese Zahl
     * ergeben ({@link #shortestDigits(double)}).
     */
    static String javaScriptNumber(double number)
    {
        if (Double.isNaN(number))
            return "NaN";
        if (Double.isInfinite(number))
            return number > 0 ? "Infinity" : "-Infinity";
        if (number == 0)
            return "0";
        if (number < 0)
            return "-" + javaScriptNumber(-number);
        BigDecimal decimal = shortestDigits(number);
        String digits = decimal.unscaledValue().toString();
        int k = digits.length();
        int n = k - decimal.scale();
        if (k <= n && n <= 21)
            return digits + "0".repeat(n - k);
        if (0 < n && n <= 21)
            return digits.substring(0, n) + "." + digits.substring(n);
        if (-6 < n && n <= 0)
            return "0." + "0".repeat(-n) + digits;
        int exponent = n - 1;
        String suffix = "e" + (exponent < 0 ? "-" : "+") + Math.abs(exponent);
        return k == 1 ? digits + suffix : digits.charAt(0) + "." + digits.substring(1) + suffix;
    }

    /**
     * Kuerzeste Dezimalzahl, die beim Einlesen wieder {@code number} ergibt; bei gleich vielen Stellen die naechste,
     * wie ECMAScript es verlangt. {@link Double#toString(double)} liefert das erst ab JDK 19 (vorher z.B.
     * "4.9E-324" statt 5e-324).
     */
    private static BigDecimal shortestDigits(double number)
    {
        BigDecimal exact = new BigDecimal(number);
        for (int precision = 1; precision <= 17; precision++)
        {
            BigDecimal below = exact.round(new MathContext(precision, RoundingMode.FLOOR));
            BigDecimal above = exact.round(new MathContext(precision, RoundingMode.CEILING));
            boolean belowFits = below.doubleValue() == number;
            boolean aboveFits = above.doubleValue() == number;
            if (belowFits && aboveFits)
            {
                int compare = exact.subtract(below).compareTo(above.subtract(exact));
                if (compare == 0)
                    return exact.round(new MathContext(precision, RoundingMode.HALF_EVEN)).stripTrailingZeros();
                return (compare < 0 ? below : above).stripTrailingZeros();
            }
            if (belowFits)
                return below.stripTrailingZeros();
            if (aboveFits)
                return above.stripTrailingZeros();
        }
        return exact.round(new MathContext(17, RoundingMode.HALF_EVEN)).stripTrailingZeros();
    }

    private static boolean missing(Object value)
    {
        return value == null || ScriptObjectMirror.isUndefined(value);
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

    record Draft(String summary, SepaTransferDraftWriter.Request request)
    {
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
