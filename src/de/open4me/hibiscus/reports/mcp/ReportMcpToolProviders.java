package de.open4me.hibiscus.reports.mcp;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.willuhn.jameica.gui.extension.Extension;
import de.willuhn.jameica.gui.extension.ExtensionRegistry;

final class ReportMcpToolProviders
{
    private ReportMcpToolProviders()
    {
    }

    static Loaded load()
    {
        ReportMcpToolRegistry registry = new ReportMcpToolRegistry();
        List<String> errors = new ArrayList<>();
        List<Extension> extensions = ExtensionRegistry.getExtensions(registry.getExtendableID());
        if (extensions != null)
        {
            for (Extension extension : extensions)
            {
                try
                {
                    extension.extend(registry);
                }
                catch (Throwable e)
                {
                    errors.add("MCP-Provider konnten nicht registriert werden: " + detail(e));
                }
            }
        }

        List<Provider> providers = new ArrayList<>();
        for (Object provider : registry.providers())
        {
            try
            {
                providers.add(new Provider(provider));
            }
            catch (Throwable e)
            {
                errors.add("Ungueltiger MCP-Provider " + provider.getClass().getName() + ": " + detail(e));
            }
        }
        return new Loaded(providers, errors);
    }

    record Loaded(List<Provider> providers, List<String> errors)
    {
        Loaded
        {
            providers = List.copyOf(providers);
            errors = List.copyOf(errors);
        }

        Provider find(String fullToolName)
        {
            for (Provider provider : providers)
            {
                if (fullToolName != null && fullToolName.startsWith(provider.prefix()))
                    return provider;
            }
            return null;
        }
    }

    static final class Provider
    {
        private final Object target;
        private final Method namespaceMethod;
        private final Method toolsMethod;
        private final Method callMethod;
        private final String namespace;

        Provider(Object target) throws Exception
        {
            this.target = target;
            this.namespaceMethod = target.getClass().getMethod("getNamespace");
            this.toolsMethod = target.getClass().getMethod("getTools");
            this.callMethod = target.getClass().getMethod("call", String.class, Map.class);
            this.namespaceMethod.setAccessible(true);
            this.toolsMethod.setAccessible(true);
            this.callMethod.setAccessible(true);
            this.namespace = cleanNamespace(String.valueOf(namespaceMethod.invoke(target)));
        }

        String namespace()
        {
            return namespace;
        }

        String prefix()
        {
            return namespace + "_";
        }

        List<Map<String, Object>> tools() throws Exception
        {
            Object result = toolsMethod.invoke(target);
            if (!(result instanceof List<?> list))
                throw new IllegalStateException("getTools muss eine Liste liefern.");
            List<Map<String, Object>> tools = new ArrayList<>();
            for (Object item : list)
            {
                if (!(item instanceof Map<?, ?> map))
                    throw new IllegalStateException("Tool-Definition muss eine Map sein.");
                Map<String, Object> tool = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet())
                    tool.put(String.valueOf(entry.getKey()), entry.getValue());
                tools.add(tool);
            }
            return tools;
        }

        Object call(String fullToolName, Map<String, Object> arguments) throws Exception
        {
            String localName = fullToolName.substring(prefix().length());
            return callMethod.invoke(target, localName, arguments);
        }

        private static String cleanNamespace(String value)
        {
            if (value == null || value.isBlank())
                throw new IllegalArgumentException("Namespace darf nicht leer sein.");
            String namespace = value.trim();
            if (!namespace.matches("[a-zA-Z0-9]+"))
                throw new IllegalArgumentException("Namespace darf nur Buchstaben und Ziffern enthalten: " + namespace);
            return namespace;
        }
    }

    private static String detail(Throwable error)
    {
        Throwable current = error;
        while (current.getCause() != null)
            current = current.getCause();
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getName() : message;
    }
}
