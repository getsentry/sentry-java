package io.sentry.event;

import io.sentry.Sentry;

/**
 * Helpers for dealing with {@link Breadcrumb}s.
 */
public final class Breadcrumbs {

    /**
     * Private constructor because this is a utility class.
     */
    private Breadcrumbs() {

    }

    /**
     * Record a {@link Breadcrumb} into all of this thread's active contexts.
     *
     * @param breadcrumb Breadcrumb to record
     */
    public static void record(Breadcrumb breadcrumb) {
        Sentry.getStoredInstance().getContext().recordBreadcrumb(breadcrumb);
    }

}
