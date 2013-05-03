package net.kencochrane.raven.event.helper;

import net.kencochrane.raven.event.EventBuilder;
import net.kencochrane.raven.event.interfaces.HttpInterface;
import net.kencochrane.raven.servlet.RavenServletRequestListener;

import javax.servlet.http.HttpServletRequest;

/**
 * EventBuilderHelper allowing to retrieve the current {@link HttpServletRequest}.
 */
public class HttpEventBuilderHelper implements EventBuilderHelper {
    @Override
    public void helpBuildingEvent(EventBuilder eventBuilder) {
        HttpServletRequest servletRequest = RavenServletRequestListener.getServletRequest();
        if (servletRequest != null) {
            eventBuilder.addSentryInterface(new HttpInterface(servletRequest));
        }
    }
}
