package de.open4me.hibiscus.reports.data;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.hubspot.jinjava.Jinjava;
import com.hubspot.jinjava.interpret.RenderResult;
import com.hubspot.jinjava.interpret.TemplateError;

import de.open4me.hibiscus.reports.api.ReportTemplateContext;
import de.willuhn.jameica.gui.extension.Extension;
import de.willuhn.jameica.gui.extension.ExtensionRegistry;

public final class DynamicReportRenderer
{
    private final Path baseDirectory;
    private final ReportAccountProvider accountProvider;
    private final ReportTransactionProvider transactionProvider;

    public DynamicReportRenderer(Path baseDirectory, ReportAccountProvider accountProvider)
    {
        this(baseDirectory, accountProvider, query -> List.of());
    }

    public DynamicReportRenderer(Path baseDirectory, ReportAccountProvider accountProvider,
                                 ReportTransactionProvider transactionProvider)
    {
        this.baseDirectory = baseDirectory;
        this.accountProvider = accountProvider;
        this.transactionProvider = transactionProvider;
    }

    public RenderedReport render(String template)
    {
        Jinjava jinjava = new Jinjava();
        jinjava.setResourceLocator(new JameicaReportResourceLoader(baseDirectory));
        ReportAccountsProxy accounts = new ReportAccountsProxy(accountProvider);
        try
        {
            List<String> errors = new ArrayList<>();
            ReportTemplateContext context = createContext(accounts, errors);
            RenderResult result = jinjava.renderForResult(template, context.objects());
            errors.addAll(result.getErrors().stream().map(DynamicReportRenderer::format).toList());
            return new RenderedReport(result.getOutput(), errors);
        }
        catch (RuntimeException e)
        {
            return new RenderedReport("", List.of("Report konnte nicht gerendert werden: " + detail(e)));
        }
    }

    private ReportTemplateContext createContext(ReportAccountsProxy accounts, List<String> errors)
    {
        Map<String, Object> objects = new LinkedHashMap<>();
        objects.put("konten", accounts);
        objects.put("kontogruppen", new ReportAccountGroupsProxy(accounts));
        objects.put("umsaetze", new ReportTransactionsProxy(transactionProvider));

        ReportTemplateContext context = new ReportTemplateContext(objects);
        List<Extension> extensions = ExtensionRegistry.getExtensions(context.getExtendableID());
        if (extensions == null || extensions.isEmpty())
            return context;

        for (Extension extension : extensions)
        {
            try
            {
                extension.extend(context);
            }
            catch (Throwable e)
            {
                errors.add("Template-Objekte konnten nicht geladen werden: " + detail(e));
            }
        }
        return context;
    }

    private static String format(TemplateError error)
    {
        if (error == null)
            return "";
        String message = error.getMessage();
        Throwable cause = error.getException();
        String detail = cause == null ? null : detail(cause);
        if (detail == null || detail.isBlank() || detail.equals(message))
            return error.toString();
        return error + System.lineSeparator() + "Ursache: " + detail;
    }

    private static String detail(Throwable error)
    {
        List<String> messages = new ArrayList<>();
        Throwable current = error;
        while (current != null)
        {
            String message = current.getMessage();
            if (message == null || message.isBlank())
                message = current.getClass().getName();
            if (messages.isEmpty() || !messages.get(messages.size() - 1).equals(message))
                messages.add(message);
            current = current.getCause();
        }
        return String.join(" -> ", messages);
    }

    public record RenderedReport(String html, List<String> errors)
    {
        public RenderedReport
        {
            html = html == null ? "" : html;
            errors = List.copyOf(errors);
        }
    }
}
