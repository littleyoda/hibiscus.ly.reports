package de.open4me.hibiscus.reports.data;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

import com.hubspot.jinjava.interpret.JinjavaInterpreter;
import com.hubspot.jinjava.loader.ResourceLocator;

public final class JameicaReportResourceLoader implements ResourceLocator
{
    private final Path baseDirectory;

    public JameicaReportResourceLoader(Path baseDirectory)
    {
        this.baseDirectory = normalize(baseDirectory);
    }

    @Override
    public String getString(String fullName, Charset encoding, JinjavaInterpreter interpreter) throws IOException
    {
        if (fullName == null || fullName.isBlank())
            throw new IOException("Leerer Template-Pfad");
        Path requested = Path.of(fullName);
        if (requested.isAbsolute())
            throw new IOException("Absolute Template-Pfade sind nicht erlaubt: " + fullName);

        Path file = normalize(baseDirectory.resolve(requested));
        if (!file.startsWith(baseDirectory))
            throw new IOException("Template liegt außerhalb des Report-Verzeichnisses: " + fullName);
        if (!Files.isRegularFile(file))
            throw new IOException("Template nicht gefunden: " + fullName);
        return Files.readString(file, encoding);
    }

    private static Path normalize(Path path)
    {
        try
        {
            return path.toRealPath();
        }
        catch (IOException e)
        {
            return path.toAbsolutePath().normalize();
        }
    }
}
