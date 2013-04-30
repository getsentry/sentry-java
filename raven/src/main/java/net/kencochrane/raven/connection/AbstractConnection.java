package net.kencochrane.raven.connection;

import net.kencochrane.raven.Raven;
import net.kencochrane.raven.event.Event;
import net.kencochrane.raven.exception.ConnectionException;

import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

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
    public static final String SENTRY_PROTOCOL_VERSION = "4";
    private static final Logger logger = Logger.getLogger(Raven.class.getCanonicalName());
    private final String publicKey;
    private final String secretKey;
    private final ReentrantLock lock = new ReentrantLock();
    /**
     * At most wait 5 minutes if the connection failed too many times.
     */
    private long maxWaitingTime = 300000;
    /**
     * When the first exception occurs, wait 10 millis before trying again.
     */
    private long baseWaitingTime = 10;
    private long waitingTime = baseWaitingTime;

    /**
     * Creates a connection based on the public and secret keys.
     *
     * @param publicKey public key (identifier) to the Sentry server.
     * @param secretKey secret key (password) to the Sentry server.
     */
    protected AbstractConnection(String publicKey, String secretKey) {
        this.publicKey = publicKey;
        this.secretKey = secretKey;
    }

    /**
     * Creates an authentication header for the sentry protocol.
     *
     * @return an authentication header as a String.
     */
    protected String getAuthHeader() {
        StringBuilder header = new StringBuilder();
        header.append("Sentry sentry_version=").append(SENTRY_PROTOCOL_VERSION);
        header.append(",sentry_client=").append(Raven.NAME);
        header.append(",sentry_key=").append(publicKey);
        header.append(",sentry_secret=").append(secretKey);
        return header.toString();
    }

    @Override
    public final void send(Event event) {
        try {
            if (!lock.isLocked()) {
                doSend(event);
                waitingTime = baseWaitingTime;
            } else {
                logger.info("The event '" + event + "' hasn't been sent to the server due to a lockdown.");
            }
        } catch (ConnectionException e) {
            lock.tryLock();
            logger.log(Level.WARNING, "An exception due to the connection occurred, a lockdown will be initiated.", e);
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
            logger.log(Level.WARNING, "Lockdown started for " + waitingTime + "ms.");
            Thread.sleep(waitingTime);

            // Double the wait until the maximum is reached
            if (waitingTime > maxWaitingTime)
                waitingTime <<= 1;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "An exception occurred during the lockdown.", e);
        } finally {
            lock.unlock();
            logger.log(Level.WARNING, "Lockdown ended.");
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
