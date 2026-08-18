package de.open4me.hibiscus.reports.automation.runtime;

import org.apache.commons.text.StringEscapeUtils;

public final class AutomationStringEscapeUtils
{
    public String escapeHtml4(String value)
    {
        return StringEscapeUtils.escapeHtml4(value);
    }
}
