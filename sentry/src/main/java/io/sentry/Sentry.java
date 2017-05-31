package io.sentry;

import io.sentry.dsn.Dsn;
import io.sentry.event.Breadcrumb;
import io.sentry.event.Event;
import io.sentry.event.EventBuilder;
import io.sentry.event.User;
import io.sentry.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sentry provides easy access to a statically stored {@link SentryClient} instance.
 */
public final class Sentry {
    private static final Logger logger = LoggerFactory.getLogger(Sentry.class);
    /**
     * The most recently constructed {@link SentryClient} instance, used by static helper
     * methods like {@link Sentry#capture(Event)}.
     */
    private static volatile SentryClient storedClient = null;

    /**
     * Hide constructor.
     */
    private Sentry() {

    }

    /**
     * Initialize and statically store a {@link SentryClient} by looking up
     * a {@link Dsn} and automatically choosing a {@link SentryClientFactory}.
     *
     * @return SentryClient
     */
    public static SentryClient init() {
        return init(null, null);
    }

    /**
     * Initialize and statically store a {@link SentryClient} by looking up
     * a {@link Dsn} and using the provided {@link SentryClientFactory}.
     *
     * @param sentryClientFactory SentryClientFactory to use.
     * @return SentryClient
     */
    public static SentryClient init(SentryClientFactory sentryClientFactory) {
        return init(null, sentryClientFactory);
    }

    /**
     * Initialize and statically store a {@link SentryClient} by using the provided
     * {@link Dsn} and automatically choosing a {@link SentryClientFactory}.
     *
     * @param dsn Data Source Name of the Sentry server.
     * @return SentryClient
     */
    public static SentryClient init(String dsn) {
        return init(dsn, null);
    }

    /**
     * Initialize and statically store a {@link SentryClient} by using the provided
     * {@link Dsn} and {@link SentryClientFactory}.
     * <p>
     * Note that the Dsn or SentryClientFactory may be null, at which a best effort attempt
     * is made to look up or choose the best value(s).
     *
     * @param dsn                 Data Source Name of the Sentry server.
     * @param sentryClientFactory SentryClientFactory to use.
     * @return SentryClient
     */
    public static SentryClient init(String dsn, SentryClientFactory sentryClientFactory) {
        SentryClient sentryClient;
        if (sentryClientFactory != null) {
            Dsn realDsn;
            if (!Util.isNullOrEmpty(dsn)) {
                realDsn = new Dsn(dsn);
            } else {
                realDsn = new Dsn(Dsn.dsnLookup());
            }

            // use the factory instance directly
            sentryClient = sentryClientFactory.createSentryClient(realDsn);
        } else {
            // do static factory lookup
            sentryClient = SentryClientFactory.sentryClient(dsn);
        }

        setStoredClient(sentryClient);
        return sentryClient;
    }

    /**
     * Returns the last statically stored {@link SentryClient} instance or null if one has
     * never been stored.
     *
     * @return statically stored {@link SentryClient} instance.
     */
    public static SentryClient getStoredClient() {
        return storedClient;
    }

    /**
     * Set the statically stored {@link SentryClient} instance.
     *
     * @param client {@link SentryClient} instance to store.
     */
    public static void setStoredClient(SentryClient client) {
        if (storedClient != null) {
            logger.warn("Overwriting statically stored SentryClient instance {} with {}.",
                storedClient, client);
        }
        storedClient = client;
    }

    private static void verifyStoredClient() {
        if (storedClient == null) {
            throw new NullPointerException("No stored SentryClient instance is available to use."
                + " You must construct a SentryClient instance before using the static Sentry methods.");
        }
    }

    /**
     * Send an Event using the statically stored {@link SentryClient} instance.
     *
     * @param event Event to send to the Sentry server.
     */
    public static void capture(Event event) {
        verifyStoredClient();
        getStoredClient().sendEvent(event);
    }

    /**
     * Sends an exception (or throwable) to the Sentry server using the statically stored
     * {@link SentryClient} instance.
     * <p>
     * The exception will be logged at the {@link Event.Level#ERROR} level.
     *
     * @param throwable exception to send to Sentry.
     */
    public static void capture(Throwable throwable) {
        verifyStoredClient();
        getStoredClient().sendException(throwable);
    }

    /**
     * Sends a message to the Sentry server using the statically stored {@link SentryClient} instance.
     * <p>
     * The message will be logged at the {@link Event.Level#INFO} level.
     *
     * @param message message to send to Sentry.
     */
    public static void capture(String message) {
        verifyStoredClient();
        getStoredClient().sendMessage(message);
    }

    /**
     * Builds and sends an {@link Event} to the Sentry server using the statically stored
     * {@link SentryClient} instance.
     *
     * @param eventBuilder {@link EventBuilder} to send to Sentry.
     */
    public static void capture(EventBuilder eventBuilder) {
        verifyStoredClient();
        getStoredClient().sendEvent(eventBuilder);
    }

    /**
     * Record a {@link Breadcrumb}.
     *
     * @param breadcrumb Breadcrumb to record.
     */
    public static void record(Breadcrumb breadcrumb) {
        verifyStoredClient();
        getStoredClient().getContext().recordBreadcrumb(breadcrumb);
    }

    /**
     * Set the {@link User} in the current context.
     *
     * @param user User to store.
     */
    public static void setUser(User user) {
        verifyStoredClient();
        getStoredClient().getContext().setUser(user);
    }

    /**
     * Clears the current context.
     */
    public static void clearContext() {
        verifyStoredClient();
        getStoredClient().getContext().clear();
    }

}
