package com.getsentry.raven.connection;

import com.getsentry.raven.environment.RavenEnvironment;
import com.getsentry.raven.event.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

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
    private final AtomicBoolean lockdown = new AtomicBoolean();
    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private final String authHeader;
    /**
     * Maximum duration for a lockdown.
     */
    private long maxWaitingTime = DEFAULT_MAX_WAITING_TIME;
    /**
     * Base duration for a lockdown.
     * <p>
     * On each attempt the time is doubled until it reaches {@link #maxWaitingTime}.
     */
    private long baseWaitingTime = DEFAULT_BASE_WAITING_TIME;
    private long waitingTime = baseWaitingTime;
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
        eventSendFailureCallbacks = new HashSet<>();
        authHeader = "Sentry sentry_version=" + SENTRY_PROTOCOL_VERSION + ","
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
            waitIfLockedDown();

            doSend(event);
            waitingTime = baseWaitingTime;
        } catch (ConnectionException e) {
            logger.warn("An exception due to the connection occurred, a lockdown will be initiated.", e);
            lockDown();

            for (EventSendFailureCallback eventSendFailureCallback : eventSendFailureCallbacks) {
                try {
                    eventSendFailureCallback.onFailure(event, e);
                } catch (Exception exc) {
                    logger.warn("An exception occurred while running an EventSendFailureCallback: "
                        + eventSendFailureCallback.getClass().getName(), exc);
                }
            }

            throw e;
        }
    }

    /**
     * Pauses the current thread if there is a lockdown.
     */
    private void waitIfLockedDown() {
        while (lockdown.get()) {
            lock.lock();
            try {
                if (lockdown.get()) {
                    condition.await();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("An exception occurred during the lockdown.", e);
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * Initiates a lockdown for {@link #waitingTime}ms and resume the paused threads once the lockdown ends.
     */
    private void lockDown() {
        if (!lockdown.compareAndSet(false, true)) {
            return;
        }

        try {
            logger.warn("Lockdown started for {}ms.", waitingTime);
            Thread.sleep(waitingTime);

            // Double the wait until the maximum is reached
            if (waitingTime < maxWaitingTime) {
                waitingTime <<= 1;
            }
        } catch (Exception e) {
            logger.warn("An exception occurred during the lockdown.", e);
        } finally {
            lockdown.set(false);

            lock.lock();
            try {
                condition.signalAll();
            } finally {
                lock.unlock();
            }
            logger.warn("Lockdown ended.");
        }
    }

    /**
     * Sends an event to the sentry server.
     *
     * @param event captured event to add in Sentry.
     * @throws ConnectionException whenever a temporary exception due to the connection happened.
     */
    protected abstract void doSend(Event event) throws ConnectionException;

    public void setMaxWaitingTime(long maxWaitingTime) {
        this.maxWaitingTime = maxWaitingTime;
    }

    public void setBaseWaitingTime(long baseWaitingTime) {
        this.baseWaitingTime = baseWaitingTime;
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
