package net.kencochrane.raven.connection;

import net.kencochrane.raven.Raven;
import net.kencochrane.raven.event.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Abstract connection to a Sentry server.
 * <p>
 * Provide the basic tools to submit events to the server (authentication header, dsn).<br />
 * To avoid spamming the network if and when Sentry is down, automatically lock the connection each time a
 * {@link ConnectionException} is caught.
 * </p>
 */
public abstract class AbstractConnection implements Connection {
    /**
     * Current sentry protocol version.
     */
    public static final String SENTRY_PROTOCOL_VERSION = "5";
    /**
     * Default maximum duration for a lockdown.
     */
    public static final long DEFAULT_MAX_WAITING_TIME = TimeUnit.MINUTES.toMillis(5);
    /**
     * Default base duration for a lockdown.
     */
    public static final long DEFAULT_BASE_WAITING_TIME = TimeUnit.MILLISECONDS.toMillis(10);
    private static final Logger logger = LoggerFactory.getLogger(AbstractConnection.class);
    private final ReentrantLock lock = new ReentrantLock();
    private final String authHeader;
    /**
     * Maximum duration for a lockdown.
     */
    private long maxWaitingTime = DEFAULT_MAX_WAITING_TIME;
    /**
     * Base duration for a lockdown.
     * <p>
     * On each attempt the time is doubled until it reaches {@link #maxWaitingTime}.
     * </p>
     */
    private long baseWaitingTime = DEFAULT_BASE_WAITING_TIME;
    private long waitingTime = baseWaitingTime;

    /**
     * Creates a connection based on the public and secret keys.
     *
     * @param publicKey public key (identifier) to the Sentry server.
     * @param secretKey secret key (password) to the Sentry server.
     */
    protected AbstractConnection(String publicKey, String secretKey) {
        authHeader = "Sentry sentry_version=" + SENTRY_PROTOCOL_VERSION + ","
                + "sentry_client=" + Raven.NAME + ","
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
    public final void send(Event event) {
        try {
            if (!lock.isLocked()) {
                doSend(event);
                waitingTime = baseWaitingTime;
            }
        } catch (ConnectionException e) {
            lock.tryLock();
            logger.warn("An exception due to the connection occurred, a lockdown will be initiated.", e);
        } finally {
            if (lock.isHeldByCurrentThread())
                lockDown();
        }
    }

    /**
     * Initiates a lockdown for {@link #waitingTime}ms and release the lock once the lockdown ends.
     */
    private void lockDown() {
        try {
            logger.warn("Lockdown started for {}ms.", waitingTime);
            Thread.sleep(waitingTime);

            // Double the wait until the maximum is reached
            if (waitingTime < maxWaitingTime)
                waitingTime <<= 1;
        } catch (Exception e) {
            logger.warn("An exception occurred during the lockdown.", e);
        } finally {
            lock.unlock();
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
}
