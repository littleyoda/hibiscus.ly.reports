package de.open4me.hibiscus.reports.duplicates;

import java.time.LocalDate;
import java.util.List;

public final class DuplicateDetectorTests
{
    private DuplicateDetectorTests()
    {
    }

    public static void run()
    {
        detectsLikelyDuplicate();
        rejectsDifferentBaseFields();
        recognizesNormalizedAndPrefixPurpose();
        reducesScoreForAdjacentIdsAndDifferentBalance();
    }

    private static void detectsLikelyDuplicate()
    {
        DuplicateDetector detector = new DuplicateDetector();

        List<DuplicateMatch> matches = detector.find(List.of(
            transaction("10", "1", "Giro", "DE1", "Rechnung 123", 100d, 900d),
            transaction("5", "1", "Giro", "DE1", "Rechnung 123", 100d, 900d)));

        checkEquals(1, matches.size(), "match count");
        checkEquals(SimilarityLevel.MITTEL, matches.get(0).level(), "exact purpose level");
        checkEquals(70, matches.get(0).score(), "exact purpose score");
    }

    private static void rejectsDifferentBaseFields()
    {
        DuplicateDetector detector = new DuplicateDetector();
        DuplicateTransaction base = transaction("10", "1", "Giro", "DE1", "Zweck", 100d, 900d);

        check(detector.find(List.of(base, transaction("9", "2", "Tagesgeld", "DE1", "Zweck", 100d, 900d)))
            .isEmpty(), "different account rejected");
        check(detector.find(List.of(base, transaction("9", "1", "Giro", "DE2", "Zweck", 100d, 900d)))
            .isEmpty(), "different counter account rejected");
        check(detector.find(List.of(base, transaction("9", "1", "Giro", "DE1", "Zweck", 100.02d, 900d)))
            .isEmpty(), "different amount rejected");
        check(detector.find(List.of(base, new DuplicateTransaction("9", "1", "Giro",
            LocalDate.of(2026, 1, 2), 100d, 900d, "DE1", "", "Zweck", ""))).isEmpty(),
            "different date rejected");
        check(detector.find(List.of(transaction("10", "1", "Giro", "DE1", "Zweck", 0d, 900d),
            transaction("9", "1", "Giro", "DE1", "Zweck", 0d, 900d))).isEmpty(), "zero amount rejected");
    }

    private static void recognizesNormalizedAndPrefixPurpose()
    {
        DuplicateDetector detector = new DuplicateDetector();

        int normalized = detector.score(transaction("10", "1", "Giro", "DE1", "A B\nC", 100d, 900d),
            transaction("5", "1", "Giro", "DE1", "ABC", 100d, 900d));
        int prefix = detector.score(transaction("10", "1", "Giro", "DE1", "Rechnung 123 Zusatz", 100d, 900d),
            transaction("5", "1", "Giro", "DE1", "Rechnung 123", 100d, 900d));

        checkEquals(70, normalized, "normalized purpose score");
        checkEquals(70, prefix, "prefix purpose score");
    }

    private static void reducesScoreForAdjacentIdsAndDifferentBalance()
    {
        DuplicateDetector detector = new DuplicateDetector();

        int score = detector.score(transaction("10", "1", "Giro", "DE1", "Zweck", 100d, 900d),
            transaction("9", "1", "Giro", "DE1", "Zweck", 100d, 800d));

        checkEquals(-10, score, "adjacent ids and different balance score");
        check(SimilarityLevel.of(score) == null, "low score ignored");
    }

    private static DuplicateTransaction transaction(String id, String accountId, String accountName,
                                                    String counterAccount, String purpose, double amount,
                                                    double balance)
    {
        return new DuplicateTransaction(id, accountId, accountName, LocalDate.of(2026, 1, 1), amount, balance,
            counterAccount, "Kategorie", purpose, "");
    }

    private static void check(boolean condition, String message)
    {
        if (!condition)
            throw new AssertionError(message);
    }

    private static void checkEquals(Object expected, Object actual, String message)
    {
        if (!java.util.Objects.equals(expected, actual))
            throw new AssertionError(message + " expected <" + expected + "> but was <" + actual + ">");
    }
}
