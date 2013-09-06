package net.kencochrane.raven.event.interfaces;

/**
 * The Exception interface for Sentry allowing to add an Exception details to an event.
 */
public class ExceptionInterface implements SentryInterface {
    /**
     * Name of the exception interface in Sentry.
     */
    public static final String EXCEPTION_INTERFACE = "sentry.interfaces.Exception";
    private final ImmutableThrowable throwable;

    /**
     * Creates a an Exception element for an {@link net.kencochrane.raven.event.Event}.
     *
     * @param throwable Exception from the JVM to send to Sentry.
     */
    public ExceptionInterface(Throwable throwable) {
        this.throwable = new ImmutableThrowable(throwable);
    }

    @Override
    public String getInterfaceName() {
        return EXCEPTION_INTERFACE;
    }

    public ImmutableThrowable getThrowable() {
        return throwable;
    }
}
