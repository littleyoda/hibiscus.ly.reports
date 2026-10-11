package de.open4me.hibiscus.reports.mcp;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public final class NgrokTunnelTests
{
    private NgrokTunnelTests()
    {
    }

    public static void run()
    {
        Path libraries = Path.of("lib").toAbsolutePath();
        String nativeJar = "ngrok-java-native-1.1.1-" + NgrokTunnel.platform() + ".jar";
        try (URLClassLoader loader = new URLClassLoader(new URL[] {
            libraries.resolve("ngrok-java-1.1.1.jar").toUri().toURL(),
            libraries.resolve(nativeJar).toUri().toURL(),
            libraries.resolve("slf4j-api-2.0.16.jar").toUri().toURL()
        }, ClassLoader.getPlatformClassLoader()))
        {
            Class<?> runtime = loader.loadClass("com.ngrok.Runtime");
            var load = runtime.getDeclaredMethod("load");
            load.setAccessible(true);
            load.invoke(null);
        }
        catch (Exception | LinkageError e)
        {
            throw new AssertionError("ngrok SDK oder Native-JAR kann nicht geladen werden: " + nativeJar, e);
        }
    }
}
