package de.open4me.hibiscus.reports.ui;

import de.willuhn.jameica.gui.Action;
import de.willuhn.util.ApplicationException;

public final class NavigationTreeSettingsAction implements Action
{
    @Override
    public void handleAction(Object context) throws ApplicationException
    {
        try
        {
            NavigationTreeSettingsDialog dialog = new NavigationTreeSettingsDialog();
            java.util.Set<String> hiddenIds = dialog.open();
            if (hiddenIds != null)
                NavigationTreeFilter.saveHiddenIds(hiddenIds);
        }
        catch (ApplicationException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new ApplicationException("Navigation-Baum konnte nicht angepasst werden: " + e.getMessage(), e);
        }
    }
}
