package io.sentry.core;

import io.sentry.core.SentryEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Provides package-private fields that should not be exposed in production to
 * test classes in other packages.
 */
public final class SentryCoreFieldsProxy {
    public static Throwable throwableFrom(@NotNull SentryEvent sentryEvent) {
        return sentryEvent.getThrowable();
    }
}
