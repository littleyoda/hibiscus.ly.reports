package de.open4me.hibiscus.reports.mcp;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.willuhn.jameica.hbci.HBCIProperties;
import de.willuhn.logging.Logger;

/**
 * Fuehrt die Verwendungszweck-Angaben einer SEPA-Ueberweisung zu einem Feld zusammen und prueft es so,
 * wie Hibiscus es beim Speichern prueft.
 *
 * <p>SEPA kennt nur einen Verwendungszweck. Hibiscus lehnt bei {@code AuslandsUeberweisung} eine zweite
 * Zeile ({@code setZweck2}) und weitere Verwendungszwecke ({@code setWeitereVerwendungszwecke}) ab.
 * {@link #merge} hat bewusst keine Abhaengigkeit auf Hibiscus und ist ohne Hibiscus testbar; nur
 * {@link #hibiscusMaxLength()} und {@link #hibiscusValidChars()} lesen die Hibiscus-Einstellungen.
 */
public final class SepaPurpose
{
    /** Standardwert von Hibiscus, wenn die Einstellung nicht gelesen werden kann (z.B. in Tests). */
    static final int DEFAULT_MAX_LENGTH = 140;

    /**
     * Standardwert von Hibiscus fuer {@code hbci.sepa.validchars}
     * ({@code HBCIProperties.HBCI_SEPA_VALIDCHARS}), wenn die Einstellung nicht gelesen werden kann.
     */
    static final String DEFAULT_VALID_CHARS =
        "abcdefghijklmnopqrstuvwxyzüöäßABCDEFGHIJKLMNOPQRSTUVWXYZÜÖÄ0123456789':?,- (+.)/&*$%";

    /**
     * Eine ganze Folge von Leerraum, nur ab ihrem Anfang gesucht (davor steht kein Leerraum), damit jede Folge
     * genau einmal gelesen wird und lange Eingaben linear bleiben. Mit (?U) ist \s der Unicode-Leerraum
     * (White_Space), also auch geschuetzte Leerzeichen und NEXT LINE (U+0085).
     */
    private static final Pattern WHITESPACE_RUN = Pattern.compile("(?U)(?<!\\s)\\s+");

    /** Die Warnung beim Rueckfall auf die Standardwerte erscheint nur einmal im Log. */
    private static volatile boolean fallbackLogged;

    private SepaPurpose()
    {
    }

    /**
     * Die in Hibiscus eingestellte Obergrenze fuer den SEPA-Verwendungszweck
     * ({@code HBCIProperties.HBCI_SEPATRANSFER_USAGE_MAXLENGTH}, Standard 140). Die Konstante wird in einem
     * statischen Initialisierer aus den Jameica-Einstellungen gelesen. Ohne Jameica-Laufzeit (Unit-Tests)
     * schlaegt das fehl; dann gilt der Standardwert. Passiert das im laufenden Hibiscus, steht eine Warnung im
     * Log, denn dann prueft das Plugin womoeglich gegen andere Werte als Hibiscus beim Speichern.
     */
    public static int hibiscusMaxLength()
    {
        try
        {
            return HBCIProperties.HBCI_SEPATRANSFER_USAGE_MAXLENGTH;
        }
        catch (LinkageError | RuntimeException e)
        {
            logFallback(e);
            return DEFAULT_MAX_LENGTH;
        }
    }

    /**
     * Die in Hibiscus eingestellten erlaubten Zeichen fuer SEPA-Texte ({@code HBCIProperties.HBCI_SEPA_VALIDCHARS}).
     * Ohne Jameica-Laufzeit gilt wie bei {@link #hibiscusMaxLength()} der Standardwert.
     */
    public static String hibiscusValidChars()
    {
        try
        {
            return HBCIProperties.HBCI_SEPA_VALIDCHARS;
        }
        catch (LinkageError | RuntimeException e)
        {
            logFallback(e);
            return DEFAULT_VALID_CHARS;
        }
    }

    private static void logFallback(Throwable e)
    {
        if (fallbackLogged)
            return;
        fallbackLogged = true;
        try
        {
            Logger.warn("Hibiscus-Einstellungen fuer den SEPA-Verwendungszweck nicht lesbar, es gelten die "
                + "Standardwerte (" + DEFAULT_MAX_LENGTH + " Zeichen, SEPA-Standardzeichensatz): " + e);
        }
        catch (LinkageError | RuntimeException ignored)
        {
            // Ohne Jameica-Logging (Unit-Tests) gibt es nichts zu melden.
        }
    }

    /**
     * {@link #merge(String, String, List, int, String)} mit der Obergrenze und den erlaubten Zeichen aus Hibiscus.
     * Was diese Methode annimmt, lehnt Hibiscus beim Speichern nicht wegen Laenge oder Zeichen ab.
     */
    public static String mergeForHibiscus(String purpose, String purpose2, List<String> additionalPurposes)
    {
        return merge(purpose, purpose2, additionalPurposes, hibiscusMaxLength(), hibiscusValidChars());
    }

    /** {@link #merge(String, String, List, int, String)} ohne Zeichenpruefung. */
    public static String merge(String purpose, String purpose2, List<String> additionalPurposes, int maxLength)
    {
        return merge(purpose, purpose2, additionalPurposes, maxLength, null);
    }

    /**
     * Verbindet {@code purpose}, {@code purpose2} und {@code additionalPurposes} mit je einem Leerzeichen.
     * Jeder Teil wird getrimmt und leere Teile entfallen. Leerraum ausser dem normalen Leerzeichen
     * (Zeilenumbrueche, Tabulatoren, geschuetzte Leerzeichen usw.) wird samt angrenzender Leerzeichen zu
     * einem Leerzeichen; normale Leerzeichen innerhalb eines Teils bleiben unveraendert. Das Ergebnis erneut
     * zusammenzufuehren aendert es nicht.
     *
     * @param maxLength Obergrenze in UTF-16-Einheiten ({@link String#length()}), so wie Hibiscus zaehlt; bei
     *        Hibiscus {@code HBCIProperties.HBCI_SEPATRANSFER_USAGE_MAXLENGTH}
     * @param validChars erlaubte Zeichen, bei Hibiscus {@code HBCIProperties.HBCI_SEPA_VALIDCHARS};
     *        {@code null} prueft keine Zeichen
     * @return der zusammengefuehrte Verwendungszweck oder {@code null}, wenn nichts uebrig bleibt
     * @throws IllegalArgumentException wenn das Ergebnis zu lang ist oder ein nicht erlaubtes Zeichen enthaelt
     */
    public static String merge(String purpose, String purpose2, List<String> additionalPurposes, int maxLength,
                               String validChars)
    {
        List<String> parts = new ArrayList<>();
        add(parts, purpose);
        add(parts, purpose2);
        if (additionalPurposes != null)
        {
            for (String part : additionalPurposes)
                add(parts, part);
        }
        if (parts.isEmpty())
            return null;

        String merged = String.join(" ", parts);
        if (merged.length() > maxLength)
            throw new IllegalArgumentException("Der Verwendungszweck ist " + merged.length()
                + " Zeichen lang; Hibiscus erlaubt maximal " + maxLength + ".");
        if (validChars != null)
            checkChars(merged, validChars);
        return merged;
    }

    private static void add(List<String> parts, String part)
    {
        if (part == null)
            return;
        // Folgen, die nur aus normalen Leerzeichen bestehen, bleiben; jede andere Folge wird ein Leerzeichen.
        String normalized = WHITESPACE_RUN.matcher(part)
            .replaceAll(run -> Matcher.quoteReplacement(onlySpaces(run.group()) ? run.group() : " "))
            .strip();
        if (!normalized.isEmpty())
            parts.add(normalized);
    }

    private static boolean onlySpaces(String run)
    {
        for (int i = 0; i < run.length(); i++)
        {
            if (run.charAt(i) != ' ')
                return false;
        }
        return true;
    }

    /** Prueft jede UTF-16-Einheit wie {@code HBCIProperties.checkChars}, nennt aber das ganze Zeichen. */
    private static void checkChars(String purpose, String validChars)
    {
        for (int i = 0; i < purpose.length(); i++)
        {
            if (validChars.indexOf(purpose.charAt(i)) < 0)
                throw new IllegalArgumentException("Der Verwendungszweck enthält das Zeichen "
                    + describe(purpose.codePointAt(i)) + ", das Hibiscus in SEPA-Überweisungen nicht erlaubt.");
        }
    }

    private static String describe(int codePoint)
    {
        String code = String.format("U+%04X", codePoint);
        if (visible(codePoint))
            return "\"" + new String(Character.toChars(codePoint)) + "\" (" + code + ")";
        return code;
    }

    private static boolean visible(int codePoint)
    {
        int type = Character.getType(codePoint);
        return !Character.isWhitespace(codePoint) && !Character.isSpaceChar(codePoint)
            && type != Character.CONTROL && type != Character.FORMAT && type != Character.UNASSIGNED
            && type != Character.PRIVATE_USE && type != Character.SURROGATE;
    }
}
