package de.open4me.hibiscus.reports.automation.runtime;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Optional;

import com.cronutils.descriptor.CronDescriptor;
import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;

public final class AutomationSchedule
{
    private final CronParser parser = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ));
    private final CronDescriptor descriptor = CronDescriptor.instance(Locale.GERMAN);

    public LocalDateTime next(String expression, LocalDateTime after)
    {
        if (expression == null || expression.isBlank())
            return null;
        Cron cron = parser.parse(toQuartz(expression.trim()));
        cron.validate();
        ZonedDateTime base = (after == null ? LocalDateTime.now() : after).atZone(java.time.ZoneId.systemDefault());
        Optional<ZonedDateTime> next = ExecutionTime.forCron(cron).nextExecution(base);
        return next.map(ZonedDateTime::toLocalDateTime).orElse(null);
    }

    public void validate(String expression)
    {
        if (expression == null || expression.isBlank())
            throw new IllegalArgumentException("Cron-Ausdruck darf nicht leer sein.");
        Cron cron = parser.parse(toQuartz(expression.trim()));
        cron.validate();
    }

    public String describe(String expression)
    {
        if (expression == null || expression.isBlank())
            return "";
        Cron cron = parser.parse(toQuartz(expression.trim()));
        cron.validate();
        return descriptor.describe(cron);
    }

    public static String normalize(String expression)
    {
        return toQuartz(expression);
    }

    private static String toQuartz(String expression)
    {
        String value = preset(expression);
        String[] parts = value.trim().split("\\s+");
        if (parts.length == 5)
            return "0 " + value;
        return value;
    }

    private static String preset(String expression)
    {
        if ("taeglich".equalsIgnoreCase(expression) || "täglich".equalsIgnoreCase(expression))
            return "0 0 8 * * ?";
        if ("woechentlich".equalsIgnoreCase(expression) || "wöchentlich".equalsIgnoreCase(expression))
            return "0 0 8 ? * MON";
        if ("monatlich".equalsIgnoreCase(expression))
            return "0 0 8 1 * ?";
        return expression;
    }
}
