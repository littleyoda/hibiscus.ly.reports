package de.open4me.hibiscus.reports.automation.runtime;

import java.util.Map;

import de.open4me.hibiscus.reports.model.ReportAccount;

public final class AutomationPaymentsTests
{
    private AutomationPaymentsTests()
    {
    }

    public static void run()
    {
        includesCustomDialogTextInSummary();
        keepsSummaryCompactWithoutCustomDialogText();
    }

    private static void includesCustomDialogTextInSummary()
    {
        String summary = AutomationPayments.summary(Map.of(
            "konto", account(),
            "empfaengerName", "Max Mustermann",
            "iban", "DE02120300000000202051",
            "bic", "BYLADEM1001",
            "betrag", "50.00",
            "verwendungszweck", "Beispiel",
            "dialogText", "Bitte Rechnung 4711 pruefen."));

        check(summary.contains("Bitte Rechnung 4711 pruefen."), "custom dialog text is shown");
        check(summary.indexOf("Bitte Rechnung 4711 pruefen.") < summary.indexOf("Konto: Giro"),
            "custom dialog text is shown before payment details");
    }

    private static void keepsSummaryCompactWithoutCustomDialogText()
    {
        String summary = AutomationPayments.summary(Map.of(
            "konto", account(),
            "empfaengerName", "Max Mustermann",
            "iban", "DE02120300000000202051",
            "betrag", "50.00",
            "verwendungszweck", "Beispiel"));

        check(summary.startsWith("Überweisung\n\nKonto: Giro"), "summary has no empty custom text section");
    }

    private static ReportAccount account()
    {
        return new ReportAccount("1", 0, 0, null, "Giro", "12030000",
            "DE02120300000000202051", "Privat", true, false, null);
    }

    private static void check(boolean value, String message)
    {
        if (!value)
            throw new AssertionError(message);
    }
}
