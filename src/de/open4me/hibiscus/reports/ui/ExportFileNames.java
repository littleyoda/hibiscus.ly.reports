package de.open4me.hibiscus.reports.ui;

import java.util.Locale;

final class ExportFileNames
{
    private ExportFileNames()
    {
    }

    static String withExtension(String filename, String extension)
    {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(extension))
            return filename;
        if (lower.endsWith(".png") || lower.endsWith(".svg"))
            return filename.substring(0, filename.length() - 4) + extension;
        return filename + extension;
    }
}

