package de.open4me.hibiscus.reports.ui;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.eclipse.swt.graphics.Image;

import de.willuhn.datasource.GenericIterator;
import de.willuhn.datasource.GenericObject;
import de.willuhn.datasource.GenericObjectNode;
import de.willuhn.datasource.pseudo.PseudoIterator;
import de.willuhn.jameica.gui.Action;
import de.willuhn.jameica.gui.Item;
import de.willuhn.jameica.gui.NavigationItem;
import de.willuhn.jameica.gui.util.SWTUtil;

final class ReportNavigationItem implements NavigationItem
{
    private final Item parent;
    private final String name;
    private final String id;
    private final String iconClose;
    private final String iconOpen;
    private final Action action;
    private final boolean expanded;
    private final List<Item> children = new ArrayList<>();
    private boolean enabled = true;

    ReportNavigationItem(Item parent, String name, String id, String icon, Action action)
    {
        this(parent, name, id, icon, icon, action, false);
    }

    ReportNavigationItem(Item parent, String name, String id, String iconClose, String iconOpen,
                         Action action, boolean expanded)
    {
        this.parent = parent;
        this.name = name;
        this.id = id;
        this.iconClose = iconClose;
        this.iconOpen = iconOpen == null ? iconClose : iconOpen;
        this.action = action;
        this.expanded = expanded;
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
        return SWTUtil.getImage(iconOpen);
    }

    @Override
    public Image getIconClose()
    {
        return SWTUtil.getImage(iconClose);
    }

    @Override
    public boolean isExpanded()
    {
        return expanded;
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
        return PseudoIterator.fromArray(children.toArray(new Item[0]));
    }

    @Override
    public boolean hasChild(GenericObjectNode object)
    {
        return children.contains(object);
    }

    @Override
    public GenericIterator getPossibleParents() throws RemoteException
    {
        return PseudoIterator.fromArray(new Item[0]);
    }

    @Override
    public GenericIterator getPath() throws RemoteException
    {
        List<Item> path = new ArrayList<>();
        if (parent != null)
            path.add(parent);
        path.add(this);
        return PseudoIterator.fromArray(path.toArray(new Item[0]));
    }

    @Override
    public void addChild(Item item)
    {
        if (item != null)
            children.add(item);
    }

    List<Item> children()
    {
        return List.copyOf(children);
    }

    void sortChildren(Comparator<Item> comparator)
    {
        children.sort(comparator);
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
        if (recursive)
        {
            for (Item child : children)
            {
                try
                {
                    child.setEnabled(enabled, true);
                }
                catch (RemoteException e)
                {
                    throw new IllegalStateException("Navigation konnte nicht aktualisiert werden", e);
                }
            }
        }
    }

    @Override
    public String getExtendableID()
    {
        return getID();
    }
}
