package net.kencochrane.raven.event.interfaces;

import java.util.Arrays;

public class StackTraceInterface implements SentryInterface {
    public static final String STACKTRACE_INTERFACE = "sentry.interfaces.Stacktrace";
    private final StackTraceElement[] stackTrace;

    public StackTraceInterface(StackTraceElement[] stackTrace) {
        this.stackTrace = Arrays.copyOf(stackTrace, stackTrace.length);
    }

    @Override
    public String getInterfaceName() {
        return STACKTRACE_INTERFACE;
    }

    public StackTraceElement[] getStackTrace() {
        return Arrays.copyOf(stackTrace, stackTrace.length);
    }
}
