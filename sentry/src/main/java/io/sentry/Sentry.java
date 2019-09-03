package io.sentry;

import io.sentry.config.Lookup;
import io.sentry.config.ResourceLoader;
import io.sentry.context.Context;
import io.sentry.dsn.Dsn;
import io.sentry.event.Breadcrumb;
import io.sentry.event.Event;
import io.sentry.event.EventBuilder;
import io.sentry.event.User;
import io.sentry.util.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sentry provides easy access to a statically stored {@link SentryClient} instance.
 */
public final class Sentry {
    private static final Logger logger = LoggerFactory.getLogger(Sentry.class);

    /**
     * A synchronization guard of the stored client. Not using the class as the guard because that could theoretically
     * deadlock with 3rd party code if it also synced on the class.
     */
    private static final Object STORED_CLIENT_ACCESS = new Object();

    /**
     * The most recently constructed {@link SentryClient} instance, used by static helper
     * methods like {@link Sentry#capture(Event)}.
     */
    private static SentryClient storedClient = null;

    /**
     * Optional override for the default resource loader used to look for properties.
     *
     * @deprecated This was a hack to be able to inject the resource loader into the static Lookup initialization.
     * This is no longer required due to {@link Lookup} being configurable and passed throughout the classes as an
     * instance.
     */
    @Deprecated
    private static ResourceLoader resourceLoader;

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
     * @deprecated use {@link #init(SentryOptions)}
     */
    @Deprecated
    public static SentryClient init() {
        return init((String) null);
    }

    /**
     * Initialize and statically store a {@link SentryClient} by looking up
     * a {@link Dsn} and using the provided {@link SentryClientFactory}.
     *
     * @param sentryClientFactory SentryClientFactory to use.
     * @return SentryClient
     *
     * @deprecated use {@link #init(SentryOptions)}
     */
    @Deprecated
    public static SentryClient init(@Nullable SentryClientFactory sentryClientFactory) {
        return init(SentryOptions.from(Lookup.getDefault(), null, sentryClientFactory));
    }

    /**
     * Initialize and statically store a {@link SentryClient} by using the provided
     * {@link Dsn} and automatically choosing a {@link SentryClientFactory}.
     *
     * @param dsn Data Source Name of the Sentry server.
     * @return SentryClient
     *
     * @deprecated use {@link #init(SentryOptions)}
     */
    @Deprecated
    public static SentryClient init(@Nullable String dsn) {
        return init(SentryOptions.defaults(dsn));
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
     * @deprecated use {@link #init(SentryOptions)}
     */
    @Deprecated
    public static SentryClient init(@Nullable String dsn, @Nullable SentryClientFactory sentryClientFactory) {
        SentryOptions options = SentryOptions.defaults(dsn);
        options.setSentryClientFactory(sentryClientFactory);
        return init(options);
    }

    /**
     * Initializes a new Sentry client from the provided context.
     *
     * <p>The canonical way of using this method is:
     * <p></p>
     * <pre>
     * {@link Lookup} lookup = ... obtain or construct the instance of this class to be able to locate Sentry config
     * String dsn = ... obtain the Sentry data source name or leave null for lookup in the configuration
     * SentryClient client =
     *   Sentry.init({@link SentryOptions}.{@link SentryOptions#from(Lookup, String) from(lookup, dsn))};
     * </pre>
     * If you want to rely on the default mechanisms to obtain the configuration, you can also use the
     * {@link SentryOptions#defaults()} method which will use the default way of obtaining the configuration and DSN
     * obtained from the configuration.
     *
     * @param sentryOptions the context using with to create the client
     * @return the Sentry client
     */
    public static SentryClient init(SentryOptions sentryOptions) {
        // Hack to allow Lookup.java access to a different resource locator before its static initializer runs.
        // v2: Lookup won't be static and this hack will be removed.
        // ResourceLocator will ba passed to Lookup upon instantiation
        Sentry.resourceLoader = sentryOptions.getResourceLoader();

        // make sure to use the DSN configured in the options instead of the one that the factory can find in
        // its lookup
        SentryClient client = sentryOptions.getSentryClientFactory().createSentryClient(sentryOptions.getDsn());
        setStoredClient(client);
        return client;
    }

    /**
     * Returns the last statically stored {@link SentryClient} instance. If no instance
     * is already stored, an attempt will be made to create a {@link SentryClient} from the configuration
     * found in the environment.
     *
     * @return statically stored {@link SentryClient} instance, or null.
     */
    public static SentryClient getStoredClient() {
        synchronized (STORED_CLIENT_ACCESS) {
            if (storedClient != null) {
                return storedClient;
            }

            init(SentryOptions.defaults());
        }

        return storedClient;
    }

    /**
     * The {@link ResourceLoader} used to lookup properties.
     *
     * @return {@link ResourceLoader}.
     * @deprecated Using this field is discouraged in favour of using the configurable {@link Lookup} with
     * {@link io.sentry.config.provider.ResourceLoaderConfigurationProvider}.
     */
    @Deprecated
    public static ResourceLoader getResourceLoader() {
        return resourceLoader;
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
        synchronized (STORED_CLIENT_ACCESS) {
            if (storedClient != null) {
                logger.warn("Overwriting statically stored SentryClient instance {} with {}.",
                        storedClient, client);
            }
            storedClient = client;
        }
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
        synchronized (STORED_CLIENT_ACCESS) {
            if (storedClient == null) {
                return;
            }

            storedClient.closeConnection();
            storedClient = null;
        }
    }

}
