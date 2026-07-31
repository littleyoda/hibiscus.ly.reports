package de.open4me.hibiscus.reports.ui;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Text;

import de.open4me.hibiscus.reports.automation.runtime.AutomationSchedule;
import de.open4me.hibiscus.reports.automation.runtime.AutomationScheduleSpec;
import de.open4me.hibiscus.reports.automation.runtime.AutomationScheduleSpec.IntervalUnit;
import de.open4me.hibiscus.reports.automation.runtime.AutomationScheduleSpec.Type;

public final class AutomationScheduleEditor extends Composite
{
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final AutomationSchedule schedule = new AutomationSchedule();
    private final Combo type;
    private final Spinner hour;
    private final Spinner minute;
    private final Map<DayOfWeek, Button> weekdayButtons = new EnumMap<>(DayOfWeek.class);
    private final Combo monthMode;
    private final Spinner monthDay;
    private final Spinner intervalAmount;
    private final Combo intervalUnit;
    private final Text expert;
    private final Text cronPreview;
    private final Text nextPreview;
    private final Text error;
    private final EditorRow timeRow;
    private final EditorRow weekdayRow;
    private final EditorRow monthRow;
    private final EditorRow intervalRow;
    private final EditorRow expertRow;
    private final EditorRow errorRow;
    private boolean refreshing;

    public AutomationScheduleEditor(Composite parent, int style)
    {
        super(parent, style);
        setLayout(new GridLayout(1, false));

        type = combo("Art", new String[] { "Kein Zeitplan", "Taeglich", "Woechentlich", "Monatlich",
            "Intervall", "Experte" });

        timeRow = row("Uhrzeit", 4);
        hour = spinner(timeRow.content, 0, 23, 8);
        new Label(timeRow.content, SWT.NONE).setText(":");
        minute = spinner(timeRow.content, 0, 59, 0);
        new Label(timeRow.content, SWT.NONE).setText("Uhr");

        weekdayRow = row("Wochentage", 7);
        weekdayButton(weekdayRow.content, DayOfWeek.MONDAY, "Mo");
        weekdayButton(weekdayRow.content, DayOfWeek.TUESDAY, "Di");
        weekdayButton(weekdayRow.content, DayOfWeek.WEDNESDAY, "Mi");
        weekdayButton(weekdayRow.content, DayOfWeek.THURSDAY, "Do");
        weekdayButton(weekdayRow.content, DayOfWeek.FRIDAY, "Fr");
        weekdayButton(weekdayRow.content, DayOfWeek.SATURDAY, "Sa");
        weekdayButton(weekdayRow.content, DayOfWeek.SUNDAY, "So");

        monthRow = row("Monatlich am", 3);
        monthMode = new Combo(monthRow.content, SWT.DROP_DOWN | SWT.READ_ONLY);
        monthMode.setItems(new String[] { "Tag des Monats", "Letzter Tag im Monat" });
        monthMode.select(0);
        monthDay = spinner(monthRow.content, 1, 31, 1);

        intervalRow = row("Intervall", 3);
        intervalAmount = spinner(intervalRow.content, 1, 59, 15);
        intervalUnit = new Combo(intervalRow.content, SWT.DROP_DOWN | SWT.READ_ONLY);
        intervalUnit.setItems(new String[] { "Minuten", "Stunden" });
        intervalUnit.select(0);

        expertRow = row("Cron-Ausdruck", 1);
        expert = new Text(expertRow.content, SWT.BORDER);
        expert.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        cronPreview = readonlyText("Cron");
        nextPreview = readonlyText("Naechster Lauf");
        errorRow = row("Fehler", 1);
        error = new Text(errorRow.content, SWT.BORDER | SWT.READ_ONLY | SWT.WRAP);
        error.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        error.setForeground(getDisplay().getSystemColor(SWT.COLOR_RED));

        addListeners(this);
        setSpec(AutomationScheduleSpec.none());
    }

    public void setExpression(String expression)
    {
        setSpec(AutomationScheduleSpec.fromExpression(expression));
    }

    public String getExpression()
    {
        AutomationScheduleSpec spec = currentSpec();
        if (spec.type() == Type.EXPERT && spec.toExpression().isBlank())
            throw new IllegalArgumentException("Cron-Ausdruck darf im Expertenmodus nicht leer sein.");
        String expression = spec.toExpression();
        if (!expression.isBlank())
            schedule.validate(expression);
        return expression;
    }

    private void setSpec(AutomationScheduleSpec spec)
    {
        type.select(spec.type().ordinal());
        hour.setSelection(spec.hour());
        minute.setSelection(spec.minute());

        for (Button button : weekdayButtons.values())
            button.setSelection(false);
        for (DayOfWeek day : spec.weekdays())
            weekdayButtons.get(day).setSelection(true);
        if (spec.type() == Type.WEEKLY && spec.weekdays().isEmpty())
            weekdayButtons.get(DayOfWeek.MONDAY).setSelection(true);

        monthMode.select(spec.lastMonthDay() ? 1 : 0);
        monthDay.setSelection(Math.max(1, Math.min(31, spec.monthDay())));
        intervalAmount.setSelection(Math.max(1, Math.min(spec.intervalUnit() == IntervalUnit.HOURS ? 24 : 59,
            spec.intervalAmount())));
        intervalUnit.select(spec.intervalUnit() == IntervalUnit.HOURS ? 1 : 0);
        expert.setText(spec.expertExpression());
        refresh();
    }

    private AutomationScheduleSpec currentSpec()
    {
        Type selectedType = Type.values()[Math.max(0, type.getSelectionIndex())];
        return switch (selectedType)
        {
            case NONE -> AutomationScheduleSpec.none();
            case DAILY -> AutomationScheduleSpec.daily(hour.getSelection(), minute.getSelection());
            case WEEKLY -> AutomationScheduleSpec.weekly(selectedWeekdays(), hour.getSelection(),
                minute.getSelection());
            case MONTHLY -> monthMode.getSelectionIndex() == 1
                ? AutomationScheduleSpec.monthlyLastDay(hour.getSelection(), minute.getSelection())
                : AutomationScheduleSpec.monthlyDay(monthDay.getSelection(), hour.getSelection(),
                    minute.getSelection());
            case INTERVAL -> AutomationScheduleSpec.interval(intervalAmount.getSelection(),
                intervalUnit.getSelectionIndex() == 1 ? IntervalUnit.HOURS : IntervalUnit.MINUTES);
            case EXPERT -> AutomationScheduleSpec.expert(expert.getText());
        };
    }

    private Set<DayOfWeek> selectedWeekdays()
    {
        Set<DayOfWeek> result = EnumSet.noneOf(DayOfWeek.class);
        for (Map.Entry<DayOfWeek, Button> entry : weekdayButtons.entrySet())
        {
            if (entry.getValue().getSelection())
                result.add(entry.getKey());
        }
        return result;
    }

    private Combo combo(String label, String[] values)
    {
        EditorRow row = row(label, 1);
        Combo combo = new Combo(row.content, SWT.DROP_DOWN | SWT.READ_ONLY);
        combo.setItems(values);
        combo.select(0);
        combo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        return combo;
    }

    private EditorRow row(String label, int columns)
    {
        Composite row = new Composite(this, SWT.NONE);
        row.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        row.setLayout(new GridLayout(2, false));

        Label rowLabel = new Label(row, SWT.NONE);
        rowLabel.setText(label);
        rowLabel.setLayoutData(new GridData(150, SWT.CENTER, false, false));

        Composite content = new Composite(row, SWT.NONE);
        content.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        content.setLayout(new GridLayout(columns, false));
        return new EditorRow(row, content);
    }

    private Spinner spinner(Composite parent, int minimum, int maximum, int value)
    {
        Spinner spinner = new Spinner(parent, SWT.BORDER);
        spinner.setMinimum(minimum);
        spinner.setMaximum(maximum);
        spinner.setSelection(value);
        return spinner;
    }

    private void weekdayButton(Composite parent, DayOfWeek day, String text)
    {
        Button button = new Button(parent, SWT.CHECK);
        button.setText(text);
        weekdayButtons.put(day, button);
    }

    private Text readonlyText(String label)
    {
        EditorRow row = row(label, 1);
        Text text = new Text(row.content, SWT.BORDER | SWT.READ_ONLY);
        text.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        return text;
    }

    private void addListeners(Composite parent)
    {
        for (org.eclipse.swt.widgets.Control child : parent.getChildren())
        {
            if (child instanceof Composite composite)
                addListeners(composite);
            if (child instanceof Combo || child instanceof Button || child instanceof Spinner || child instanceof Text)
                child.addListener(SWT.Modify, event -> refresh());
            if (child instanceof Combo || child instanceof Button || child instanceof Spinner)
                child.addListener(SWT.Selection, event -> refresh());
        }
    }

    private void refresh()
    {
        if (refreshing)
            return;
        refreshing = true;
        Type selectedType = Type.values()[Math.max(0, type.getSelectionIndex())];
        try
        {
            setVisible(timeRow, selectedType == Type.DAILY || selectedType == Type.WEEKLY
                || selectedType == Type.MONTHLY);
            setVisible(weekdayRow, selectedType == Type.WEEKLY);
            setVisible(monthRow, selectedType == Type.MONTHLY);
            setVisible(intervalRow, selectedType == Type.INTERVAL);
            setVisible(expertRow, selectedType == Type.EXPERT);
            monthDay.setEnabled(monthMode.getSelectionIndex() == 0);
            intervalAmount.setMaximum(intervalUnit.getSelectionIndex() == 1 ? 24 : 59);
            if (intervalAmount.getSelection() > intervalAmount.getMaximum())
                intervalAmount.setSelection(intervalAmount.getMaximum());

            String expression = currentSpec().toExpression();
            if (selectedType == Type.EXPERT && expression.isBlank())
                throw new IllegalArgumentException("Cron-Ausdruck darf im Expertenmodus nicht leer sein.");
            cronPreview.setText(expression);
            if (expression.isBlank())
                nextPreview.setText("");
            else
            {
                LocalDateTime next = schedule.next(expression, LocalDateTime.now());
                nextPreview.setText(next == null ? "" : DATE_TIME.format(next));
            }
            error.setText("");
            setVisible(errorRow, false);
        }
        catch (Exception e)
        {
            cronPreview.setText("");
            nextPreview.setText("");
            error.setText(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            setVisible(errorRow, true);
        }
        finally
        {
            refreshing = false;
            layout(true, true);
            getParent().layout(true, true);
        }
    }

    private static void setVisible(EditorRow row, boolean visible)
    {
        row.container.setVisible(visible);
        GridData data = (GridData) row.container.getLayoutData();
        data.exclude = !visible;
    }

    private static final class EditorRow
    {
        private final Composite container;
        private final Composite content;

        private EditorRow(Composite container, Composite content)
        {
            this.container = container;
            this.content = content;
        }
    }
}
