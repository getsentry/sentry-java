package net.kencochrane.raven.event.interfaces;

import java.util.*;

public class StackTraceInterface implements SentryInterface {
    public static final String STACKTRACE_INTERFACE = "sentry.interfaces.Stacktrace";
    private final ImmutableThrowable throwable;

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
