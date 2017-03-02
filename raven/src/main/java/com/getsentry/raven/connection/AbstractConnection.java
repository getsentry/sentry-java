package com.getsentry.raven.connection;

import com.getsentry.raven.environment.RavenEnvironment;
import com.getsentry.raven.event.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * Abstract connection to a Sentry server.
 * <p>
 * Provide the basic tools to submit events to the server (authentication header, dsn).<br>
 * To avoid spamming the network if and when Sentry is down, automatically lock the connection each time a
 * {@link ConnectionException} is caught.
 */
public abstract class AbstractConnection implements Connection {
    /**
     * Current Sentry protocol version.
     */
    public static final String SENTRY_PROTOCOL_VERSION = "6";
    private static final Logger logger = LoggerFactory.getLogger(AbstractConnection.class);
    /**
     * Value of the X-Sentry-Auth header.
     */
    private final String authHeader;
    /**
     * Set of callbacks that will be called when an exception occurs while attempting to
     * send events to the Sentry server.
     */
    private Set<EventSendFailureCallback> eventSendFailureCallbacks;
    private LockdownManager lockdownManager;

    /**
     * Creates a connection based on the public and secret keys.
     *
     * @param publicKey public key (identifier) to the Sentry server.
     * @param secretKey secret key (password) to the Sentry server.
     */
    protected AbstractConnection(String publicKey, String secretKey) {
        this.lockdownManager = new LockdownManager();
        this.eventSendFailureCallbacks = new HashSet<>();
        this.authHeader = "Sentry sentry_version=" + SENTRY_PROTOCOL_VERSION + ","
            + "sentry_client=" + RavenEnvironment.getRavenName() + ","
            + "sentry_key=" + publicKey + ","
            + "sentry_secret=" + secretKey;
    }

    /**
     * Creates an authentication header for the Sentry protocol.
     *
     * @return an authentication header as a String.
     */
    protected String getAuthHeader() {
        return authHeader;
    }

    @Override
    public final void send(Event event) throws ConnectionException {
        try {
            if (lockdownManager.isLockedDown()) {
                /*
                An exception is thrown to signal that this Event was not sent, which may be
                important in, for example, a BufferedConnection where the Event would be deleted
                from the Buffer if an exception isn't raised in the call to send.
                 */
                throw new LockedDownException("Dropping an Event due to lockdown: " + event);
            }

            doSend(event);

            lockdownManager.resetLockdown();
        } catch (ConnectionException e) {
            for (EventSendFailureCallback eventSendFailureCallback : eventSendFailureCallbacks) {
                try {
                    eventSendFailureCallback.onFailure(event, e);
                } catch (Exception exc) {
                    logger.warn("An exception occurred while running an EventSendFailureCallback: "
                        + eventSendFailureCallback.getClass().getName(), exc);
                }
            }

            logger.warn("An exception due to the connection occurred, a lockdown will be initiated.", e);
            lockdownManager.setLockdownState(e);

            throw e;
        }
    }

    /**
     * Sends an event to the Sentry server.
     *
     * @param event captured event to add in Sentry.
     * @throws ConnectionException whenever a temporary exception due to the connection happened.
     */
    protected abstract void doSend(Event event) throws ConnectionException;

    /**
     * Set the maximum waiting time for a lockdown, in milliseconds.
     *
     * @param maxWaitingTime maximum waiting time for a lockdown, in milliseconds.
     * @deprecated slated for removal
     */
    @Deprecated
    public void setMaxWaitingTime(long maxWaitingTime) {
        lockdownManager.setMaxLockdownTime(maxWaitingTime);
    }

    /**
     * Set the base waiting time for a lockdown, in milliseconds.
     *
     * @param baseWaitingTime base waiting time for a lockdown, in milliseconds.
     * @deprecated slated for removal
     */
    @Deprecated
    public void setBaseWaitingTime(long baseWaitingTime) {
        lockdownManager.setBaseLockdownTime(baseWaitingTime);
    }

    /**
     * Add a callback that is called when an exception occurs while attempting to
     * send events to the Sentry server.
     *
     * @param eventSendFailureCallback callback instance
     */
    public void addEventSendFailureCallback(EventSendFailureCallback eventSendFailureCallback) {
        eventSendFailureCallbacks.add(eventSendFailureCallback);
    }

}
