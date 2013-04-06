package net.kencochrane.raven.event.interfaces;

public class StackTraceInterface implements SentryInterface {
    public static final String STACKTRACE_INTERFACE = "sentry.interfaces.Stacktrace";
    private final ImmutableThrowable throwable;

    //TODO: Base this interface on a unique stacktrace (rather than an entire exception)
    //This should be done when the exception system in Sentry will be improved to support chained exception
    //For now, a fake stacktrace (containing the parent exceptions and their stacktraces) will be used.
    public StackTraceInterface(Throwable throwable) {
        this.throwable = new ImmutableThrowable(throwable);
    }

    @Override
    public String getInterfaceName() {
        return STACKTRACE_INTERFACE;
    }

    public ImmutableThrowable getThrowable() {
        return throwable;
    }
}
