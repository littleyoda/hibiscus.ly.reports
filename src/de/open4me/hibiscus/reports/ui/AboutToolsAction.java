package de.open4me.hibiscus.reports.ui;

import de.willuhn.jameica.gui.Action;
import de.willuhn.util.ApplicationException;

public final class AboutToolsAction implements Action
{
    @Override
    public void handleAction(Object context) throws ApplicationException
    {
        try
        {
            new AboutToolsDialog().open();
        }
        catch (Exception e)
        {
            throw new ApplicationException("Der Dialog konnte nicht geoeffnet werden: " + e.getMessage(), e);
        }
    }
}
