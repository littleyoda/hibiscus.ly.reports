package de.open4me.hibiscus.reports.mcp;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Loads only the native ngrok library for the current platform. */
final class NgrokTunnel implements AutoCloseable
{
    private static final String VERSION = "1.1.1";
    private static URLClassLoader sdkLoader;

    private final AutoCloseable session;
    private final AutoCloseable forwarder;
    private final String endpoint;

    private NgrokTunnel(AutoCloseable session, AutoCloseable forwarder, String endpoint)
    {
        this.session = session;
        this.forwarder = forwarder;
        this.endpoint = endpoint;
    }

    static NgrokTunnel open(String authtoken, int port, String ingressSecret) throws Exception
    {
        URLClassLoader loader = sdkLoader();
        AutoCloseable session = null;
        AutoCloseable forwarder = null;
        try
        {
            Class<?> sessionType = loader.loadClass("com.ngrok.Session");
            Object builder = sessionType.getMethod("withAuthtoken", String.class).invoke(null, authtoken);
            session = (AutoCloseable) invoke(builder, "connect");
            Object http = invoke(session, "httpEndpoint");
            http = http.getClass().getMethod("removeRequestHeader", String.class)
                .invoke(http, McpHttpServer.TUNNEL_HEADER);
            http = http.getClass().getMethod("addRequestHeader", String.class, String.class)
                .invoke(http, McpHttpServer.TUNNEL_HEADER, ingressSecret);
            forwarder = (AutoCloseable) http.getClass().getMethod("forward", URL.class)
                .invoke(http, new URL("http://127.0.0.1:" + port));
            String url = (String) invoke(forwarder, "getUrl");
            if (url == null || !url.startsWith("https://"))
                throw new IOException("ngrok hat keinen HTTPS-Endpoint bereitgestellt.");
            return new NgrokTunnel(session, forwarder, url + "/mcp");
        }
        catch (Exception | LinkageError e)
        {
            closeQuietly(forwarder);
            closeQuietly(session);
            throw e;
        }
    }

    private static synchronized URLClassLoader sdkLoader() throws Exception
    {
        if (sdkLoader != null)
            return sdkLoader;
        Path libraryDir = libraryDirectory();
        Path api = libraryDir.resolve("ngrok-java-" + VERSION + ".jar");
        Path nativeLibrary = libraryDir.resolve("ngrok-java-native-" + VERSION + "-" + platform() + ".jar");
        Path logging = libraryDir.resolve("slf4j-api-2.0.16.jar");
        if (!Files.isRegularFile(api) || !Files.isRegularFile(nativeLibrary) || !Files.isRegularFile(logging))
            throw new IOException("ngrok SDK fuer dieses Betriebssystem fehlt: " + nativeLibrary.getFileName());

        sdkLoader = new URLClassLoader(new URL[] {
            api.toUri().toURL(), nativeLibrary.toUri().toURL(), logging.toUri().toURL()
        }, ClassLoader.getPlatformClassLoader());
        return sdkLoader;
    }

    String endpoint()
    {
        return endpoint;
    }

    @Override
    public void close() throws Exception
    {
        try
        {
            if (forwarder != null)
                forwarder.close();
        }
        finally
        {
            session.close();
        }
    }

    private static Object invoke(Object target, String method) throws Exception
    {
        try
        {
            return target.getClass().getMethod(method).invoke(target);
        }
        catch (InvocationTargetException e)
        {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception)
                throw exception;
            throw e;
        }
    }

    private static Path libraryDirectory() throws Exception
    {
        Path location = Path.of(NgrokTunnel.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path pluginDirectory = Files.isDirectory(location) ? location.getParent().getParent() : location.getParent();
        return pluginDirectory.resolve("lib");
    }

    static String platform()
    {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        String cpu = arch.equals("amd64") || arch.equals("x86_64") ? "x86_64"
            : arch.equals("aarch64") || arch.equals("arm64") ? "aarch_64" : "unsupported";
        if (os.contains("linux"))
            return "linux-" + cpu;
        if (os.contains("mac"))
            return "osx-" + cpu;
        if (os.contains("win"))
            return "windows-" + cpu;
        return "unsupported";
    }

    private static void closeQuietly(AutoCloseable resource)
    {
        if (resource == null)
            return;
        try
        {
            resource.close();
        }
        catch (Exception ignored)
        {
        }
    }
}
