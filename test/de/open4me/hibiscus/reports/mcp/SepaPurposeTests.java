package de.open4me.hibiscus.reports.mcp;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class SepaPurposeTests
{
    private static final int MAX = 140;

    private SepaPurposeTests()
    {
    }

    public static void run()
    {
        mergesAllPartsWithSpaces();
        keepsSinglePurposeUnchanged();
        mergesOnlySecondPurpose();
        mergesOnlyAdditionalPurposes();
        skipsEmptyAndBlankParts();
        returnsNullWithoutContent();
        toleratesMissingAdditionalPurposes();
        trimsParts();
        replacesLineBreaksAndTabsWithSpace();
        collapsesWhitespaceInsideParts();
        acceptsExactlyMaxLength();
        rejectsOneCharacterOverMaxLength();
        countsUtf16UnitsLikeHibiscus();
        measuresLengthAfterNormalization();
        usesGivenMaxLength();
        hibiscusMaxLengthFallsBackToDefaultWithoutRuntime();
        keepsPlainSpacesInsideParts();
        acceptsAllowedCharacters();
        rejectsCharactersOutsideAllowedSet();
        skipsCharacterCheckWithoutAllowedSet();
        hibiscusValidCharsFallsBackToDefaultWithoutRuntime();
        mergeForHibiscusUsesHibiscusRules();
        mergingMergedPurposeKeepsIt();
        normalizesLongSpaceRunsInLinearTime();
    }

    private static void mergesAllPartsWithSpaces()
    {
        checkEquals("Rechnung 4711 Kd-Nr 0815 vom 01.01.2026 Auftrag 123456",
            SepaPurpose.merge("Rechnung 4711 Kd-Nr 0815", "vom 01.01.2026",
                List.of("Auftrag 123456"), MAX),
            "all parts joined by one space");
        checkEquals("a b c d", SepaPurpose.merge("a", "b", List.of("c", "d"), MAX),
            "several additional purposes keep their order");
    }

    private static void keepsSinglePurposeUnchanged()
    {
        checkEquals("Rechnung 123", SepaPurpose.merge("Rechnung 123", null, List.of(), MAX),
            "single purpose unchanged");
    }

    private static void mergesOnlySecondPurpose()
    {
        checkEquals("zweite Zeile", SepaPurpose.merge(null, "zweite Zeile", List.of(), MAX),
            "only purpose2");
        checkEquals("erste zweite", SepaPurpose.merge("erste", "zweite", null, MAX),
            "purpose and purpose2 only");
    }

    private static void mergesOnlyAdditionalPurposes()
    {
        checkEquals("x y", SepaPurpose.merge("", "", List.of("x", "y"), MAX), "only additional purposes");
    }

    private static void skipsEmptyAndBlankParts()
    {
        checkEquals("a b", SepaPurpose.merge("a", "", Arrays.asList("", "   ", null, "b"), MAX),
            "empty, blank and null entries are skipped");
        checkEquals("a", SepaPurpose.merge("a", "  ", List.of(" \t "), MAX), "blank purpose2 adds no space");
    }

    private static void returnsNullWithoutContent()
    {
        check(SepaPurpose.merge(null, null, null, MAX) == null, "all null");
        check(SepaPurpose.merge("", "", List.of(), MAX) == null, "all empty");
        check(SepaPurpose.merge("  ", "\n", Arrays.asList(" ", null), MAX) == null, "only whitespace");
    }

    private static void toleratesMissingAdditionalPurposes()
    {
        checkEquals("a b", SepaPurpose.merge("a", "b", null, MAX), "no additional purposes list");
    }

    private static void trimsParts()
    {
        checkEquals("a b c", SepaPurpose.merge("  a ", "\tb\n", List.of(" c  "), MAX), "parts trimmed");
    }

    private static void replacesLineBreaksAndTabsWithSpace()
    {
        checkEquals("Zeile 1 Zeile 2 Spalte A Spalte B",
            SepaPurpose.merge("Zeile 1\nZeile 2", "Spalte A\tSpalte B", null, MAX),
            "line breaks and tabs become spaces");
        checkEquals("a b c d", SepaPurpose.merge("a\r\nb", null, List.of("c\rd"), MAX),
            "carriage returns become spaces");
        checkEquals("a b", SepaPurpose.merge("a\u00A0b", null, null, MAX), "no-break space becomes space");
    }

    private static void collapsesWhitespaceInsideParts()
    {
        checkEquals("a b c", SepaPurpose.merge("a  \n\t  b", null, List.of("c"), MAX),
            "runs of whitespace become one space");
        checkEquals("a b", SepaPurpose.merge("a\u0085b", null, null, MAX), "next line (U+0085) becomes space");
        check(SepaPurpose.merge("\u0085", " \u0085 ", null, MAX) == null, "only next line (U+0085)");
        checkEquals("a b c", SepaPurpose.merge("a\u2028b\u3000c", null, null, MAX),
            "line separator and ideographic space become spaces");
    }

    private static void acceptsExactlyMaxLength()
    {
        String first = "x".repeat(70);
        String second = "y".repeat(69);
        String merged = SepaPurpose.merge(first, second, null, MAX);
        checkEquals(MAX, merged.length(), "merged length is exactly the maximum");
        checkEquals(first + " " + second, merged, "exact maximum accepted unchanged");
        checkEquals("z".repeat(MAX), SepaPurpose.merge("z".repeat(MAX), null, null, MAX),
            "single part of exactly the maximum accepted");
    }

    private static void rejectsOneCharacterOverMaxLength()
    {
        String message = rejection("x".repeat(70), "y".repeat(70), null, MAX);
        checkEquals("Der Verwendungszweck ist 141 Zeichen lang; Hibiscus erlaubt maximal 140.", message,
            "message with actual and allowed length");
        check(rejection("x".repeat(141), null, null, MAX).contains("141"), "single part over the maximum");
    }

    private static void countsUtf16UnitsLikeHibiscus()
    {
        // Hibiscus prueft die Laenge mit String.length(); ein Zeichen ausserhalb der BMP zaehlt doppelt.
        String emoji = new String(Character.toChars(0x1F600));
        String fits = emoji.repeat(MAX / 2);
        checkEquals(fits, SepaPurpose.merge(fits, null, null, MAX), "70 emojis are 140 UTF-16 units");
        checkEquals("Der Verwendungszweck ist 141 Zeichen lang; Hibiscus erlaubt maximal 140.",
            rejection(fits + "x", null, null, MAX), "length counted in UTF-16 units");
    }

    private static void measuresLengthAfterNormalization()
    {
        String merged = SepaPurpose.merge("a".repeat(70) + "\n\n\n", "   " + "b".repeat(69), null, MAX);
        checkEquals(MAX, merged.length(), "surrounding and repeated whitespace does not count");
    }

    private static void usesGivenMaxLength()
    {
        checkEquals("abcde", SepaPurpose.merge("abcde", null, null, 5), "fits a smaller maximum");
        checkEquals("Der Verwendungszweck ist 6 Zeichen lang; Hibiscus erlaubt maximal 5.",
            rejection("abcdef", null, null, 5), "smaller maximum is enforced");
        checkEquals("a".repeat(200), SepaPurpose.merge("a".repeat(200), null, null, 200),
            "larger maximum is accepted");
    }

    private static void hibiscusMaxLengthFallsBackToDefaultWithoutRuntime()
    {
        // Ohne Jameica-Laufzeit scheitert der statische Initialisierer von HBCIProperties; dann gilt 140.
        checkEquals(140, SepaPurpose.hibiscusMaxLength(), "default limit without Jameica runtime");
    }

    private static void keepsPlainSpacesInsideParts()
    {
        checkEquals("RF18 5390  0754 7034", SepaPurpose.merge("RF18 5390  0754 7034", null, null, MAX),
            "single purpose with double spaces unchanged");
        checkEquals("a  b c", SepaPurpose.merge("a  b", "c", null, MAX), "double spaces kept when merging");
        checkEquals("a b", SepaPurpose.merge("a  \n  b", null, null, MAX),
            "spaces around a line break collapse with it");
    }

    private static void acceptsAllowedCharacters()
    {
        checkEquals("Rechnung 4711 vom 01.01.2026", SepaPurpose.merge("Rechnung 4711", "vom 01.01.2026", null,
            MAX, SepaPurpose.DEFAULT_VALID_CHARS), "default SEPA characters accepted");
        checkEquals("Grüße & Dank (Teil 1/2)", SepaPurpose.merge("Grüße & Dank (Teil 1/2)", null, null,
            MAX, SepaPurpose.DEFAULT_VALID_CHARS), "umlauts and punctuation accepted");
    }

    private static void rejectsCharactersOutsideAllowedSet()
    {
        checkEquals("Der Verwendungszweck enthält das Zeichen \"€\" (U+20AC), das Hibiscus in "
                + "SEPA-Überweisungen nicht erlaubt.",
            charRejection("Rechnung 4711", "100 €"), "visible character named");
        checkEquals("Der Verwendungszweck enthält das Zeichen U+200B, das Hibiscus in "
                + "SEPA-Überweisungen nicht erlaubt.",
            charRejection("Rechnung\u200B4711", null), "invisible character shown as code point");
        check(charRejection("Danke \uD83D\uDE00", null).contains("U+1F600"), "emoji shown as one code point");
        check(charRejection("a", "b\u00A0c").isEmpty(), "no-break space becomes a space before the check");
    }

    private static void skipsCharacterCheckWithoutAllowedSet()
    {
        checkEquals("100 €", SepaPurpose.merge("100 €", null, null, MAX, null), "no check without a set");
    }

    private static void hibiscusValidCharsFallsBackToDefaultWithoutRuntime()
    {
        checkEquals("abcdefghijklmnopqrstuvwxyzüöäßABCDEFGHIJKLMNOPQRSTUVWXYZÜÖÄ0123456789':?,- (+.)/&*$%",
            SepaPurpose.hibiscusValidChars(), "Hibiscus default SEPA characters without Jameica runtime");
    }

    private static void mergeForHibiscusUsesHibiscusRules()
    {
        checkEquals("Rechnung 4711", SepaPurpose.mergeForHibiscus("Rechnung", "4711", null), "merged");
        check(hibiscusRejection("x".repeat(141)).contains("141"), "Hibiscus default length");
        check(hibiscusRejection("100 €").contains("U+20AC"), "Hibiscus default characters");
    }

    private static void mergingMergedPurposeKeepsIt()
    {
        String merged = SepaPurpose.merge(" Rechnung  4711\n", "\tvom 01.01.2026", List.of("Auftrag 1"), MAX,
            SepaPurpose.DEFAULT_VALID_CHARS);
        checkEquals("Rechnung  4711 vom 01.01.2026 Auftrag 1", merged, "merged once");
        checkEquals(merged, SepaPurpose.merge(merged, null, List.of(), MAX, SepaPurpose.DEFAULT_VALID_CHARS),
            "merging the merged purpose again changes nothing");
    }

    private static void normalizesLongSpaceRunsInLinearTime()
    {
        // Eine lange Folge normaler Leerzeichen ohne anderen Leerraum darf nicht quadratisch viel Arbeit machen;
        // die Normalisierung laeuft vor der Laengenpruefung, also auch fuer uebergrosse Eingaben.
        String spaces = " ".repeat(100_000);
        long start = System.nanoTime();
        checkEquals("a" + spaces + "b", SepaPurpose.merge("a" + spaces + "b", null, null, Integer.MAX_VALUE),
            "long run of spaces kept");
        long millis = (System.nanoTime() - start) / 1_000_000;
        check(millis < 2_000, "long run of spaces normalized in " + millis + " ms");
    }

    private static String charRejection(String purpose, String purpose2)
    {
        try
        {
            SepaPurpose.merge(purpose, purpose2, null, MAX, SepaPurpose.DEFAULT_VALID_CHARS);
        }
        catch (IllegalArgumentException e)
        {
            return e.getMessage();
        }
        return "";
    }

    private static String hibiscusRejection(String purpose)
    {
        try
        {
            SepaPurpose.mergeForHibiscus(purpose, null, null);
        }
        catch (IllegalArgumentException e)
        {
            return e.getMessage();
        }
        throw new AssertionError("expected IllegalArgumentException");
    }

    private static String rejection(String purpose, String purpose2, List<String> additional, int max)
    {
        try
        {
            SepaPurpose.merge(purpose, purpose2, additional, max);
        }
        catch (IllegalArgumentException e)
        {
            return e.getMessage();
        }
        throw new AssertionError("expected IllegalArgumentException");
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
