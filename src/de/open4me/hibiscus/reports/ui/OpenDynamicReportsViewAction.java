package de.open4me.hibiscus.reports.ui;

import de.willuhn.jameica.gui.Action;
import de.willuhn.jameica.gui.GUI;
import de.willuhn.util.ApplicationException;

public class OpenDynamicReportsViewAction implements Action
{
    @Override
    public void handleAction(Object context) throws ApplicationException
    {
        GUI.startView(DynamicReportsView.class, context);
    }
}
