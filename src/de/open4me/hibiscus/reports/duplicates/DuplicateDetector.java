package de.open4me.hibiscus.reports.duplicates;

import java.util.Comparator;
import java.util.List;

public final class DuplicateDetector
{
    private static final double AMOUNT_EPSILON = 0.01d;
    private static final double NON_ZERO_EPSILON = 0.009d;
    private static final double BALANCE_EPSILON = 0.001d;
    private static final int STEP = 20;
    private static final int BASE_SCORE = 50;

    public List<DuplicateMatch> find(List<DuplicateTransaction> transactions)
    {
        List<DuplicateTransaction> sorted = transactions.stream()
            .sorted(Comparator
                .comparing(DuplicateTransaction::accountId)
                .thenComparing(DuplicateTransaction::date, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparingDouble(DuplicateTransaction::amount)
                .thenComparing(Comparator.comparingLong((DuplicateTransaction transaction) ->
                    idNumber(transaction.id())).reversed()))
            .toList();

        java.util.ArrayList<DuplicateMatch> result = new java.util.ArrayList<>();
        DuplicateTransaction previous = null;
        for (DuplicateTransaction current : sorted)
        {
            if (previous != null)
            {
                int score = score(current, previous);
                SimilarityLevel level = SimilarityLevel.of(score);
                if (level != null)
                    result.add(new DuplicateMatch(result.size() + 1, current, previous, score, level));
            }
            previous = current;
        }
        return List.copyOf(result);
    }

    public int score(DuplicateTransaction left, DuplicateTransaction right)
    {
        if (!sameBase(left, right))
            return Integer.MIN_VALUE;

        String leftPurpose = left.purpose();
        String rightPurpose = right.purpose();
        if (leftPurpose.isBlank() || rightPurpose.isBlank())
            return Integer.MIN_VALUE;

        int score = BASE_SCORE;
        long distance = Math.abs(idNumber(left.id()) - idNumber(right.id()));
        if (leftPurpose.equals(rightPurpose))
        {
            score += distance < 2 ? -2 * STEP : STEP;
        }
        else
        {
            String normalizedLeft = normalizePurpose(leftPurpose);
            String normalizedRight = normalizePurpose(rightPurpose);
            if (normalizedLeft.equals(normalizedRight))
                score += STEP;
            else if (normalizedLeft.startsWith(normalizedRight) || normalizedRight.startsWith(normalizedLeft))
                score += STEP;
            else
                score -= STEP;
        }

        if (Math.abs(left.balance() - right.balance()) > BALANCE_EPSILON)
            score -= STEP;
        return score;
    }

    private static boolean sameBase(DuplicateTransaction left, DuplicateTransaction right)
    {
        if (left == null || right == null)
            return false;
        if (Math.abs(left.amount()) <= NON_ZERO_EPSILON)
            return false;
        if (Math.abs(left.amount() - right.amount()) >= AMOUNT_EPSILON)
            return false;
        if (!java.util.Objects.equals(left.date(), right.date()))
            return false;
        if (!left.accountId().equals(right.accountId()))
            return false;
        return counterAccount(left).equals(counterAccount(right));
    }

    private static String counterAccount(DuplicateTransaction transaction)
    {
        String value = transaction.counterAccountNumber();
        return value == null || value.isBlank() ? "1" : value;
    }

    private static String normalizePurpose(String value)
    {
        return value == null ? "" : value.replace("\n", "").replace(" ", "");
    }

    private static long idNumber(String id)
    {
        try
        {
            return Long.parseLong(id);
        }
        catch (Exception e)
        {
            return Long.MIN_VALUE;
        }
    }
}
