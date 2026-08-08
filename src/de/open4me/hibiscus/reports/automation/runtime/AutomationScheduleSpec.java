package de.open4me.hibiscus.reports.automation.runtime;

import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

public final class AutomationScheduleSpec
{
    public enum Type
    {
        NONE, DAILY, WEEKLY, MONTHLY, INTERVAL, EXPERT
    }

    public enum IntervalUnit
    {
        MINUTES, HOURS
    }

    private final Type type;
    private final int hour;
    private final int minute;
    private final Set<DayOfWeek> weekdays;
    private final int monthDay;
    private final boolean lastMonthDay;
    private final int intervalAmount;
    private final IntervalUnit intervalUnit;
    private final String expertExpression;

    private AutomationScheduleSpec(Type type, int hour, int minute, Set<DayOfWeek> weekdays, int monthDay,
                                   boolean lastMonthDay, int intervalAmount, IntervalUnit intervalUnit,
                                   String expertExpression)
    {
        this.type = type;
        this.hour = hour;
        this.minute = minute;
        this.weekdays = weekdays == null || weekdays.isEmpty() ? EnumSet.noneOf(DayOfWeek.class)
            : EnumSet.copyOf(weekdays);
        this.monthDay = monthDay;
        this.lastMonthDay = lastMonthDay;
        this.intervalAmount = intervalAmount;
        this.intervalUnit = intervalUnit == null ? IntervalUnit.MINUTES : intervalUnit;
        this.expertExpression = expertExpression == null ? "" : expertExpression.trim();
    }

    public static AutomationScheduleSpec none()
    {
        return new AutomationScheduleSpec(Type.NONE, 8, 0, null, 1, false, 15, IntervalUnit.MINUTES, "");
    }

    public static AutomationScheduleSpec daily(int hour, int minute)
    {
        return new AutomationScheduleSpec(Type.DAILY, hour, minute, null, 1, false, 15, IntervalUnit.MINUTES, "");
    }

    public static AutomationScheduleSpec weekly(Set<DayOfWeek> weekdays, int hour, int minute)
    {
        return new AutomationScheduleSpec(Type.WEEKLY, hour, minute, weekdays, 1, false, 15,
            IntervalUnit.MINUTES, "");
    }

    public static AutomationScheduleSpec monthlyDay(int monthDay, int hour, int minute)
    {
        return new AutomationScheduleSpec(Type.MONTHLY, hour, minute, null, monthDay, false, 15,
            IntervalUnit.MINUTES, "");
    }

    public static AutomationScheduleSpec monthlyLastDay(int hour, int minute)
    {
        return new AutomationScheduleSpec(Type.MONTHLY, hour, minute, null, 1, true, 15,
            IntervalUnit.MINUTES, "");
    }

    public static AutomationScheduleSpec interval(int amount, IntervalUnit unit)
    {
        return new AutomationScheduleSpec(Type.INTERVAL, 8, 0, null, 1, false, amount, unit, "");
    }

    public static AutomationScheduleSpec expert(String expression)
    {
        return new AutomationScheduleSpec(Type.EXPERT, 8, 0, null, 1, false, 15, IntervalUnit.MINUTES,
            expression);
    }

    public static AutomationScheduleSpec fromExpression(String expression)
    {
        if (expression == null || expression.isBlank())
            return none();

        String normalized = AutomationSchedule.normalize(expression);
        String[] parts = normalized.split("\\s+");
        if (parts.length != 6 || !"0".equals(parts[0]) || !"*".equals(parts[4]))
            return expert(expression);

        Integer minute = number(parts[1]);
        Integer hour = number(parts[2]);
        if (minute != null && hour != null)
        {
            if ("*".equals(parts[3]) && "?".equals(parts[5]))
                return daily(hour, minute);
            if ("?".equals(parts[3]) && isWeekdayList(parts[5]))
                return weekly(parseWeekdays(parts[5]), hour, minute);
            if ("?".equals(parts[5]))
            {
                if ("L".equals(parts[3]))
                    return monthlyLastDay(hour, minute);
                Integer monthDay = number(parts[3]);
                if (monthDay != null && monthDay >= 1 && monthDay <= 31)
                    return monthlyDay(monthDay, hour, minute);
            }
        }

        if ("0".equals(parts[1]) && parts[2].startsWith("0/") && "*".equals(parts[3]) && "?".equals(parts[5]))
        {
            Integer amount = number(parts[2].substring(2));
            if (amount != null)
                return interval(amount, IntervalUnit.HOURS);
        }

        if (parts[1].startsWith("0/") && "*".equals(parts[2]) && "*".equals(parts[3]) && "?".equals(parts[5]))
        {
            Integer amount = number(parts[1].substring(2));
            if (amount != null)
                return interval(amount, IntervalUnit.MINUTES);
        }

        return expert(expression);
    }

    public String toExpression()
    {
        return switch (type)
        {
            case NONE -> "";
            case DAILY -> String.format("0 %d %d * * ?", minute, hour);
            case WEEKLY -> String.format("0 %d %d ? * %s", minute, hour, weekdayExpression());
            case MONTHLY -> String.format("0 %d %d %s * ?", minute, hour, lastMonthDay ? "L" : monthDay);
            case INTERVAL -> intervalUnit == IntervalUnit.HOURS
                ? String.format("0 0 0/%d * * ?", intervalAmount)
                : String.format("0 0/%d * * * ?", intervalAmount);
            case EXPERT -> expertExpression;
        };
    }

    public String describe()
    {
        return switch (type)
        {
            case NONE -> "Kein Auslöser";
            case DAILY -> "Taeglich um " + time();
            case WEEKLY -> "Woechentlich " + weekdayDescription() + " um " + time();
            case MONTHLY -> (lastMonthDay ? "Monatlich am letzten Tag" : "Monatlich am " + monthDay + ".")
                + " um " + time();
            case INTERVAL -> intervalUnit == IntervalUnit.HOURS
                ? "Alle " + intervalAmount + " Stunde" + (intervalAmount == 1 ? "" : "n")
                : "Alle " + intervalAmount + " Minute" + (intervalAmount == 1 ? "" : "n");
            case EXPERT -> expertExpression.isBlank() ? "Experten-Cron" : expertDescription(expertExpression);
        };
    }

    public Type type()
    {
        return type;
    }

    public int hour()
    {
        return hour;
    }

    public int minute()
    {
        return minute;
    }

    public Set<DayOfWeek> weekdays()
    {
        return weekdays.isEmpty() ? EnumSet.noneOf(DayOfWeek.class) : EnumSet.copyOf(weekdays);
    }

    public int monthDay()
    {
        return monthDay;
    }

    public boolean lastMonthDay()
    {
        return lastMonthDay;
    }

    public int intervalAmount()
    {
        return intervalAmount;
    }

    public IntervalUnit intervalUnit()
    {
        return intervalUnit;
    }

    public String expertExpression()
    {
        return expertExpression;
    }

    private String weekdayExpression()
    {
        if (weekdays.isEmpty())
            throw new IllegalArgumentException("Mindestens ein Wochentag muss ausgewaehlt sein.");
        return weekdays.stream().map(AutomationScheduleSpec::cronDay).collect(Collectors.joining(","));
    }

    private String weekdayDescription()
    {
        if (weekdays.isEmpty())
            return "";
        return weekdays.stream().map(AutomationScheduleSpec::shortDay).collect(Collectors.joining(", "));
    }

    private String time()
    {
        return String.format("%02d:%02d Uhr", hour, minute);
    }

    private static String expertDescription(String expression)
    {
        try
        {
            String description = new AutomationSchedule().describe(expression);
            return description.isBlank() ? "Experten-Cron: " + expression : description;
        }
        catch (Exception e)
        {
            return "Experten-Cron: " + expression;
        }
    }

    private static String shortDay(DayOfWeek day)
    {
        return switch (day)
        {
            case MONDAY -> "Mo";
            case TUESDAY -> "Di";
            case WEDNESDAY -> "Mi";
            case THURSDAY -> "Do";
            case FRIDAY -> "Fr";
            case SATURDAY -> "Sa";
            case SUNDAY -> "So";
        };
    }

    private static boolean isWeekdayList(String value)
    {
        for (String part : value.split(","))
        {
            if (day(part) == null)
                return false;
        }
        return true;
    }

    private static Set<DayOfWeek> parseWeekdays(String value)
    {
        Set<DayOfWeek> result = EnumSet.noneOf(DayOfWeek.class);
        for (String part : value.split(","))
            result.add(day(part));
        return result;
    }

    private static String cronDay(DayOfWeek day)
    {
        return switch (day)
        {
            case MONDAY -> "MON";
            case TUESDAY -> "TUE";
            case WEDNESDAY -> "WED";
            case THURSDAY -> "THU";
            case FRIDAY -> "FRI";
            case SATURDAY -> "SAT";
            case SUNDAY -> "SUN";
        };
    }

    private static DayOfWeek day(String value)
    {
        return switch (value.toUpperCase())
        {
            case "MON" -> DayOfWeek.MONDAY;
            case "TUE" -> DayOfWeek.TUESDAY;
            case "WED" -> DayOfWeek.WEDNESDAY;
            case "THU" -> DayOfWeek.THURSDAY;
            case "FRI" -> DayOfWeek.FRIDAY;
            case "SAT" -> DayOfWeek.SATURDAY;
            case "SUN" -> DayOfWeek.SUNDAY;
            default -> null;
        };
    }

    private static Integer number(String value)
    {
        try
        {
            return Integer.valueOf(value);
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }
}
