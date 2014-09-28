package net.kencochrane.raven.event.helper;

import net.kencochrane.raven.event.EventBuilder;
import net.kencochrane.raven.event.interfaces.HttpInterface;
import net.kencochrane.raven.event.interfaces.UserInterface;
import net.kencochrane.raven.servlet.RavenServletRequestListener;

import javax.servlet.http.HttpServletRequest;

/**
 * EventBuilderHelper allowing to retrieve the current {@link HttpServletRequest}.
 * <p>
 * The {@link HttpServletRequest} is retrieved from a {@link ThreadLocal} storage. This means that this builder must
 * be called from the thread in which the HTTP request has been handled.
 */
public class HttpEventBuilderHelper implements EventBuilderHelper {
    @Override
    public void helpBuildingEvent(EventBuilder eventBuilder) {
        HttpServletRequest servletRequest = RavenServletRequestListener.getServletRequest();
        if (servletRequest == null)
            return;

        addHttpInterface(eventBuilder, servletRequest);
        addUserInterface(eventBuilder, servletRequest);
    }

    private void addHttpInterface(EventBuilder eventBuilder, HttpServletRequest servletRequest) {
        eventBuilder.withSentryInterface(new HttpInterface(servletRequest), false);
    }

    private void addUserInterface(EventBuilder eventBuilder, HttpServletRequest servletRequest) {
        String username = null;
        if (servletRequest.getUserPrincipal() != null) {
            username = servletRequest.getUserPrincipal().getName();
        }

        UserInterface userInterface = new UserInterface(null, username, servletRequest.getRemoteAddr(), null);
        eventBuilder.withSentryInterface(userInterface, false);
    }
}
