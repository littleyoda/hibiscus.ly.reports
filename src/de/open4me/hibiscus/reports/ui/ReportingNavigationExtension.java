package de.open4me.hibiscus.reports.ui;

import java.rmi.RemoteException;

import org.eclipse.swt.graphics.Image;

import de.willuhn.datasource.GenericIterator;
import de.willuhn.datasource.GenericObject;
import de.willuhn.datasource.GenericObjectNode;
import de.willuhn.datasource.pseudo.PseudoIterator;
import de.willuhn.jameica.gui.Action;
import de.willuhn.jameica.gui.Item;
import de.willuhn.jameica.gui.NavigationItem;
import de.willuhn.jameica.gui.extension.Extendable;
import de.willuhn.jameica.gui.extension.Extension;
import de.willuhn.jameica.gui.util.SWTUtil;

public class ReportingNavigationExtension implements Extension
{
    @Override
    public void extend(Extendable extendable)
    {
        if (!(extendable instanceof Item parent))
            return;
        try
        {
            parent.addChild(new ReportNavigationItem(parent, "Geldfluss",
                "hibiscus.navi.reporting.geldfluss", new OpenFlowViewAction()));
            parent.addChild(new ReportNavigationItem(parent, "Monatsübersicht",
                "hibiscus.navi.reporting.monatsuebersicht", new OpenMonthlyOverviewAction()));
            parent.addChild(new ReportNavigationItem(parent, "Saldo nach Gruppen",
                "hibiscus.navi.reporting.saldonachgruppen", new OpenGroupBalanceViewAction()));
        }
        catch (RemoteException e)
        {
            throw new IllegalStateException("Navigation konnte nicht erweitert werden", e);
        }
    }

    private static final class ReportNavigationItem implements NavigationItem
    {
        private final Item parent;
        private final String name;
        private final String id;
        private final Action action;
        private boolean enabled = true;

        private ReportNavigationItem(Item parent, String name, String id, Action action)
        {
            this.parent = parent;
            this.name = name;
            this.id = id;
            this.action = action;
        }

        @Override
        public String getName()
        {
            return name;
        }

        @Override
        public Action getAction()
        {
            return action;
        }

        @Override
        public Image getIconOpen()
        {
            return getIconClose();
        }

        @Override
        public Image getIconClose()
        {
            return SWTUtil.getImage("office-chart-area.png");
        }

        @Override
        public boolean isExpanded()
        {
            return false;
        }

        @Override
        public String getID()
        {
            return id;
        }

        @Override
        public String getPrimaryAttribute()
        {
            return "name";
        }

        @Override
        public Object getAttribute(String name)
        {
            return "name".equals(name) ? getName() : null;
        }

        @Override
        public String[] getAttributeNames()
        {
            return new String[] { "name" };
        }

        @Override
        public boolean equals(GenericObject other) throws RemoteException
        {
            return other != null && getID().equals(other.getID());
        }

        @Override
        public GenericObjectNode getParent()
        {
            return parent;
        }

        @Override
        public GenericIterator getChildren() throws RemoteException
        {
            return PseudoIterator.fromArray(new Item[0]);
        }

        @Override
        public boolean hasChild(GenericObjectNode object)
        {
            return false;
        }

        @Override
        public GenericIterator getPossibleParents() throws RemoteException
        {
            return PseudoIterator.fromArray(new Item[0]);
        }

        @Override
        public GenericIterator getPath() throws RemoteException
        {
            return PseudoIterator.fromArray(new Item[] { parent, this });
        }

        @Override
        public void addChild(Item item)
        {
            throw new UnsupportedOperationException("Blattelement");
        }

        @Override
        public boolean isEnabled()
        {
            return enabled;
        }

        @Override
        public void setEnabled(boolean enabled, boolean recursive)
        {
            this.enabled = enabled;
        }

        @Override
        public String getExtendableID()
        {
            return getID();
        }
    }
}
