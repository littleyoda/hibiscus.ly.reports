package de.open4me.hibiscus.reports.duplicates;

public record DuplicateMatch(int pairId, DuplicateTransaction first, DuplicateTransaction second, int score,
                             SimilarityLevel level)
{
}
