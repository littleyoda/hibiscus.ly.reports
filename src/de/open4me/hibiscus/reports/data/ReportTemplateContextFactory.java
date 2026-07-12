package de.open4me.hibiscus.reports.data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.open4me.hibiscus.reports.api.ReportTemplateContext;
import de.willuhn.jameica.gui.extension.Extension;
import de.willuhn.jameica.gui.extension.ExtensionRegistry;

public final class ReportTemplateContextFactory
{
    private final ReportAccountProvider accountProvider;
    private final ReportTransactionProvider transactionProvider;

    public ReportTemplateContextFactory(ReportAccountProvider accountProvider,
                                        ReportTransactionProvider transactionProvider)
    {
        this.accountProvider = accountProvider;
        this.transactionProvider = transactionProvider;
    }

    public ReportTemplateContext create(List<String> errors)
    {
        ReportAccountsProxy accounts = new ReportAccountsProxy(accountProvider);
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
                if (errors != null)
                    errors.add("Template-Objekte konnten nicht geladen werden: " + detail(e));
            }
        }
        return context;
    }

    private static String detail(Throwable error)
    {
        StringBuilder builder = new StringBuilder();
        Throwable current = error;
        while (current != null)
        {
            String message = current.getMessage();
            if (message == null || message.isBlank())
                message = current.getClass().getName();
            if (builder.length() == 0 || !builder.toString().endsWith(message))
            {
                if (builder.length() > 0)
                    builder.append(" -> ");
                builder.append(message);
            }
            current = current.getCause();
        }
        return builder.toString();
    }
}
