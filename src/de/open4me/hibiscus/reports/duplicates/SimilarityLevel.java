package de.open4me.hibiscus.reports.duplicates;

public enum SimilarityLevel
{
    NIEDRIG("niedrig", 11, 30),
    MITTEL("mittel", 31, 70),
    HOCH("hoch", 71, 90),
    SEHR_HOCH("sehr hoch", 91, Integer.MAX_VALUE);

    private final String label;
    private final int from;
    private final int to;

    SimilarityLevel(String label, int from, int to)
    {
        this.label = label;
        this.from = from;
        this.to = to;
    }

    public String label()
    {
        return label;
    }

    public static SimilarityLevel of(int score)
    {
        for (SimilarityLevel level : values())
        {
            if (score >= level.from && score <= level.to)
                return level;
        }
        return null;
    }
}
