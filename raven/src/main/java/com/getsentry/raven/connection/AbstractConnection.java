package com.getsentry.raven.connection;

import com.getsentry.raven.environment.RavenEnvironment;
import com.getsentry.raven.event.Event;
import com.getsentry.raven.time.Clock;
import com.getsentry.raven.time.SystemClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Abstract connection to a Sentry server.
 * <p>
 * Provide the basic tools to submit events to the server (authentication header, dsn).<br>
 * To avoid spamming the network if and when Sentry is down, automatically lock the connection each time a
 * {@link ConnectionException} is caught.
 */
public abstract class AbstractConnection implements Connection {
    /**
     * Current sentry protocol version.
     */
    public static final String SENTRY_PROTOCOL_VERSION = "6";
    /**
     * Default maximum duration for a lockdown.
     */
    public static final long DEFAULT_MAX_WAITING_TIME = TimeUnit.MINUTES.toMillis(5);
    /**
     * Default base duration for a lockdown.
     */
    public static final long DEFAULT_BASE_WAITING_TIME = TimeUnit.MILLISECONDS.toMillis(10);
    private static final Logger logger = LoggerFactory.getLogger(AbstractConnection.class);
    /**
     * Value of the X-Sentry-Auth header.
     */
    private final String authHeader;
    /**
     * Clock instance used for time, injectable for testing.
     */
    private final Clock clock;
    /**
     * Maximum duration for a lockdown, in milliseconds.
     */
    private long maxLockdownWaitingTime = DEFAULT_MAX_WAITING_TIME;
    /**
     * Base duration for a lockdown, in milliseconds.
     * <p>
     * On each attempt the time is doubled until it reaches {@link #maxLockdownWaitingTime}.
     */
    private long baseLockdownWaitingTime = DEFAULT_BASE_WAITING_TIME;
    /**
     * Number of milliseconds after lockdownStartTime to lockdown for, or 0 if not currently locked down.
     */
    private long lockdownWaitingTime = 0;
    /**
     * Timestamp of when the current lockdown started, or null if not currently locked down.
     */
    private Date lockdownStartTime = null;
    /**
     * Set of callbacks that will be called when an exception occurs while attempting to
     * send events to the Sentry server.
     */
    private Set<EventSendFailureCallback> eventSendFailureCallbacks;

    /**
     * Creates a connection based on the public and secret keys.
     *
     * @param publicKey public key (identifier) to the Sentry server.
     * @param secretKey secret key (password) to the Sentry server.
     */
    protected AbstractConnection(String publicKey, String secretKey) {
        this.clock = new SystemClock();
        this.eventSendFailureCallbacks = new HashSet<>();
        this.authHeader = "Sentry sentry_version=" + SENTRY_PROTOCOL_VERSION + ","
                + "sentry_client=" + RavenEnvironment.getRavenName() + ","
                + "sentry_key=" + publicKey + ","
                + "sentry_secret=" + secretKey;
    }

    /**
     * Creates an authentication header for the sentry protocol.
     *
     * @return an authentication header as a String.
     */
    protected String getAuthHeader() {
        return authHeader;
    }

    @Override
    public final void send(Event event) throws ConnectionException {
        try {
            if (isLockedDown()) {
                logger.debug("Dropped an Event due to lockdown: {}", event);
                return;
            }

            doSend(event);

            resetLockdown();
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
            setLockdownState(e);

            throw e;
        }
    }

    private synchronized boolean isLockedDown() {
        return lockdownStartTime != null && (clock.millis() - lockdownStartTime.getTime()) < lockdownWaitingTime;
    }

    private synchronized void resetLockdown() {
        lockdownWaitingTime = 0;
        lockdownStartTime = null;
    }

    private synchronized void setLockdownState(ConnectionException connectionException) {
        // If we are already in a lockdown state, don't change anything
        if (isLockedDown()) {
            return;
        }

        if (connectionException.getRecommendedLockdownWaitTime() != null) {
            lockdownWaitingTime = connectionException.getRecommendedLockdownWaitTime();
        } else if (lockdownWaitingTime != 0) {
            lockdownWaitingTime = lockdownWaitingTime * 2;
        } else {
            lockdownWaitingTime = baseLockdownWaitingTime;
        }

        lockdownWaitingTime = Math.min(maxLockdownWaitingTime, lockdownWaitingTime);
        lockdownStartTime = clock.date();
    }

    /**
     * Sends an event to the Sentry server.
     *
     * @param event captured event to add in Sentry.
     * @throws ConnectionException whenever a temporary exception due to the connection happened.
     */
    protected abstract void doSend(Event event) throws ConnectionException;

    public void setMaxWaitingTime(long maxWaitingTime) {
        this.maxLockdownWaitingTime = maxWaitingTime;
    }

    public void setBaseWaitingTime(long baseWaitingTime) {
        this.baseLockdownWaitingTime = baseWaitingTime;
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
