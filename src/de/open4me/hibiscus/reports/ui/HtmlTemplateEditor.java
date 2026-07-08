package de.open4me.hibiscus.reports.ui;

import java.util.ArrayDeque;
import java.util.Deque;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ExtendedModifyEvent;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Listener;

public final class HtmlTemplateEditor
{
    private static final int HISTORY_LIMIT = 200;

    private final StyledText text;
    private final Deque<Edit> undo = new ArrayDeque<>();
    private final Deque<Edit> redo = new ArrayDeque<>();
    private boolean applyingEdit;

    public HtmlTemplateEditor(Composite parent, Listener modifyListener)
    {
        text = new StyledText(parent, SWT.BORDER | SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
        text.setFont(createEditorFont(text.getDisplay()));
        text.setTabs(2);
        text.addDisposeListener(event -> text.getFont().dispose());
        text.addExtendedModifyListener(this::recordEdit);
        text.addVerifyKeyListener(this::handleKey);
        if (modifyListener != null)
            text.addListener(SWT.Modify, modifyListener);
    }

    public Control getControl()
    {
        return text;
    }

    public boolean isDisposed()
    {
        return text.isDisposed();
    }

    public String getText()
    {
        return text.getText();
    }

    public void setText(String value)
    {
        applyingEdit = true;
        try
        {
            text.setText(value == null ? "" : value);
            clearUndoHistory();
        }
        finally
        {
            applyingEdit = false;
        }
    }

    public boolean setFocus()
    {
        return text.setFocus();
    }

    public void clearUndoHistory()
    {
        undo.clear();
        redo.clear();
    }

    private void recordEdit(ExtendedModifyEvent event)
    {
        if (applyingEdit)
            return;
        String insertedText = event.length == 0 ? "" : text.getTextRange(event.start, event.length);
        push(undo, new Edit(event.start, insertedText, event.replacedText == null ? "" : event.replacedText));
        redo.clear();
    }

    private void handleKey(VerifyEvent event)
    {
        boolean primary = (event.stateMask & SWT.MOD1) != 0;
        if (primary && event.keyCode == 'z')
        {
            if ((event.stateMask & SWT.SHIFT) != 0)
                redo();
            else
                undo();
            event.doit = false;
            return;
        }
        if (primary && event.keyCode == 'y')
        {
            redo();
            event.doit = false;
            return;
        }
        if (primary && event.keyCode == 'a')
        {
            text.selectAll();
            event.doit = false;
            return;
        }
        if (!primary && (event.stateMask & SWT.ALT) == 0 && event.character == '\t')
        {
            insertSpaces();
            event.doit = false;
        }
    }

    private void undo()
    {
        if (undo.isEmpty())
            return;
        Edit edit = undo.removeLast();
        apply(edit.start(), edit.insertedText().length(), edit.replacedText());
        text.setSelection(edit.start() + edit.replacedText().length());
        push(redo, edit);
    }

    private void redo()
    {
        if (redo.isEmpty())
            return;
        Edit edit = redo.removeLast();
        apply(edit.start(), edit.replacedText().length(), edit.insertedText());
        text.setSelection(edit.start() + edit.insertedText().length());
        push(undo, edit);
    }

    private void insertSpaces()
    {
        Point selection = text.getSelectionRange();
        text.replaceTextRange(selection.x, selection.y, "  ");
        text.setSelection(selection.x + 2);
    }

    private void apply(int start, int length, String replacement)
    {
        applyingEdit = true;
        try
        {
            text.replaceTextRange(start, length, replacement);
        }
        finally
        {
            applyingEdit = false;
        }
    }

    private static void push(Deque<Edit> stack, Edit edit)
    {
        stack.addLast(edit);
        while (stack.size() > HISTORY_LIMIT)
            stack.removeFirst();
    }

    private static Font createEditorFont(Display display)
    {
        return new Font(display, "Monospace", 10, SWT.NORMAL);
    }

    private record Edit(int start, String insertedText, String replacedText)
    {
    }
}
