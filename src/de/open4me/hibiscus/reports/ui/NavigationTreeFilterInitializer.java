package de.open4me.hibiscus.reports.ui;

import de.willuhn.jameica.gui.GUI;
import de.willuhn.jameica.messaging.Message;
import de.willuhn.jameica.messaging.MessageConsumer;
import de.willuhn.jameica.messaging.SystemMessage;

public final class NavigationTreeFilterInitializer implements MessageConsumer
{
    @Override
    public Class[] getExpectedMessageTypes()
    {
        return new Class[] { SystemMessage.class };
    }

    @Override
    public void handleMessage(Message message)
    {
        SystemMessage systemMessage = (SystemMessage) message;
        if (systemMessage.getStatusCode() != SystemMessage.SYSTEM_STARTED)
            return;
        GUI.getDisplay().asyncExec(NavigationTreeFilter::apply);
    }

    @Override
    public boolean autoRegister()
    {
        return true;
    }
}
