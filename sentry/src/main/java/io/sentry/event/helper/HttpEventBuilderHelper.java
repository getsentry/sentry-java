package io.sentry.event.helper;

import io.sentry.event.EventBuilder;
import io.sentry.event.interfaces.HttpInterface;
import io.sentry.event.interfaces.UserInterface;
import io.sentry.servlet.SentryServletRequestListener;

import javax.servlet.http.HttpServletRequest;

/**
 * EventBuilderHelper allowing to retrieve the current {@link HttpServletRequest}.
 * <p>
 * The {@link HttpServletRequest} is retrieved from a {@link ThreadLocal} storage. This means that this builder must
 * be called from the thread in which the HTTP request has been handled.
 */
public class HttpEventBuilderHelper implements EventBuilderHelper {

    private final RemoteAddressResolver remoteAddressResolver;

    /**
     * {@link EventBuilderHelper} that uses a {@link BasicRemoteAddressResolver}.
     */
    public HttpEventBuilderHelper() {
        this.remoteAddressResolver = new BasicRemoteAddressResolver();
    }

    /**
     * {@link EventBuilderHelper} that uses the provided {@link RemoteAddressResolver}.
     *
     * @param remoteAddressResolver RemoteAddressResolver
     */
    public HttpEventBuilderHelper(RemoteAddressResolver remoteAddressResolver) {
        this.remoteAddressResolver = remoteAddressResolver;
    }

    @Override
    public void helpBuildingEvent(EventBuilder eventBuilder) {
        HttpServletRequest servletRequest = SentryServletRequestListener.getServletRequest();
        if (servletRequest == null) {
            return;
        }

        addHttpInterface(eventBuilder, servletRequest);
        addUserInterface(eventBuilder, servletRequest);
    }

    private void addHttpInterface(EventBuilder eventBuilder, HttpServletRequest servletRequest) {
        eventBuilder.withSentryInterface(new HttpInterface(servletRequest, remoteAddressResolver), false);
    }

    private void addUserInterface(EventBuilder eventBuilder, HttpServletRequest servletRequest) {
        String username = null;
        if (servletRequest.getUserPrincipal() != null) {
            username = servletRequest.getUserPrincipal().getName();
        }

        UserInterface userInterface = new UserInterface(null, username,
            remoteAddressResolver.getRemoteAddress(servletRequest), null);
        eventBuilder.withSentryInterface(userInterface, false);
    }

    public RemoteAddressResolver getRemoteAddressResolver() {
        return remoteAddressResolver;
    }
}
