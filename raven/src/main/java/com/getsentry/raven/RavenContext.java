package com.getsentry.raven;

import com.getsentry.raven.event.Breadcrumb;
import com.getsentry.raven.util.CircularFifoQueue;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * RavenContext is used to hold {@link ThreadLocal} context data (such as
 * {@link Breadcrumb}s) so that data may be collected in different parts
 * of an application and then sent together when (e.g.) an exception occurs.
 */
public class RavenContext implements AutoCloseable {
    /**
     * Thread local set of activate context objects.
     */
    private static ThreadLocal<Set<RavenContext>> activeContexts =
        new ThreadLocal<Set<RavenContext>>() {
            @Override
            protected Set<RavenContext> initialValue() {
                return new HashSet<>();
            }
    };
    /**
     * The number of {@link Breadcrumb}s to keep in the ring buffer by default.
     */
    private static final int DEFAULT_BREADCRUMB_LIMIT = 100;
    /**
     * Ring buffer of {@link Breadcrumb} objects.
     */
    private CircularFifoQueue<Breadcrumb> breadcrumbs;

    /**
     * Create a new (empty) RavenContext object.
     */
    public RavenContext() {
        // TODO: ringbuffer size parameter
        breadcrumbs = new CircularFifoQueue<>(DEFAULT_BREADCRUMB_LIMIT);
    }

    /**
     * Add this context to the activate contexts for this thread.
     */
    public void activate() {
        activeContexts.get().add(this);
    }

    /**
     * Remove this context from the activate contexts for this thread.
     */
    public void deactivate() {
        activeContexts.get().remove(this);
    }

    /**
     * Clear state from this context.
     */
    public void clear() {
        breadcrumbs.clear();
        // TODO: deactivate if not main thread id like python does?
        //   https://github.com/getsentry/raven-python/blob/master/raven/context.py#L127-L133
    }

    /**
     * Calls deactivate, used by try-with-resources ({@link AutoCloseable}).
     */
    @Override
    public void close() {
        deactivate();
    }

    /**
     * Returns all activate contexts for the current thread.
     *
     * @return Set of active {@link RavenContext} objects.
     */
    public static Set<RavenContext> getActiveContexts() {
        return Collections.unmodifiableSet(activeContexts.get());
    }

    /**
     * Return {@link Breadcrumb}s attached to this RavenContext.
     *
     * @return Iterator of {@link Breadcrumb}s.
     */
    public Iterator<Breadcrumb> getBreadcrumbs() {
        return breadcrumbs.iterator();
    }

}
