package io.sentry;

import io.sentry.context.Context;
import io.sentry.dsn.Dsn;
import io.sentry.event.Breadcrumb;
import io.sentry.event.Event;
import io.sentry.event.EventBuilder;
import io.sentry.event.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

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
     * Tracks whether the {@link #init()} method has already been attempted automatically
     * by {@link #getStoredClient()}.
     */
    private static AtomicBoolean autoInitAttempted = new AtomicBoolean(false);

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
        SentryClient sentryClient = SentryClientFactory.sentryClient(dsn, sentryClientFactory);
        setStoredClient(sentryClient);
        return sentryClient;
    }

    /**
     * Returns the last statically stored {@link SentryClient} instance. If no instance
     * is already stored, the {@link #init()} method will be called one time in an attempt to
     * create a {@link SentryClient}.
     *
     * @return statically stored {@link SentryClient} instance, or null.
     */
    public static SentryClient getStoredClient() {
        if (storedClient != null) {
            return storedClient;
        }

        synchronized (Sentry.class) {
            if (storedClient == null && !autoInitAttempted.get()) {
                // attempt initialization by using configuration found in the environment
                autoInitAttempted.set(true);
                init();
            }
        }

        return storedClient;
    }

    /**
     * Returns the {@link Context} on the statically stored {@link SentryClient}.
     *
     * @return the {@link Context} on the statically stored {@link SentryClient}.
     */
    public static Context getContext() {
        return getStoredClient().getContext();
    }

    /**
     * Clears the current context.
     */
    public static void clearContext() {
        getStoredClient().clearContext();
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

    /**
     * Send an Event using the statically stored {@link SentryClient} instance.
     *
     * @param event Event to send to the Sentry server.
     */
    public static void capture(Event event) {
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
        getStoredClient().sendMessage(message);
    }

    /**
     * Builds and sends an {@link Event} to the Sentry server using the statically stored
     * {@link SentryClient} instance.
     *
     * @param eventBuilder {@link EventBuilder} to send to Sentry.
     */
    public static void capture(EventBuilder eventBuilder) {
        getStoredClient().sendEvent(eventBuilder);
    }

    /**
     * Record a {@link Breadcrumb}.
     *
     * @param breadcrumb Breadcrumb to record.
     * @deprecated use {@link Sentry#getContext()} and then {@link Context#recordBreadcrumb(Breadcrumb)}.
     */
    @Deprecated
    public static void record(Breadcrumb breadcrumb) {
        getStoredClient().getContext().recordBreadcrumb(breadcrumb);
    }

    /**
     * Set the {@link User} in the current context.
     *
     * @param user User to store.
     * @deprecated use {@link Sentry#getContext()} and then {@link Context#setUser(User)}.
     */
    @Deprecated
    public static void setUser(User user) {
        getStoredClient().getContext().setUser(user);
    }

    /**
     * Close the stored {@link SentryClient}'s connections and remove it from static storage.
     */
    public static void close() {
        if (storedClient == null) {
            return;
        }

        storedClient.closeConnection();
        storedClient = null;

        // Allow the client to be auto initialized on the next use.
        autoInitAttempted.set(false);
    }

}
