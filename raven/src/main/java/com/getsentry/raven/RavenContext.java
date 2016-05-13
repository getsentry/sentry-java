package com.getsentry.raven;

import com.getsentry.raven.event.Breadcrumb;
import com.getsentry.raven.util.CircularFifoQueue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RavenContext is used to hold {@link ThreadLocal} context data (such as
 * {@link Breadcrumb}s) so that data may be collected in different parts
 * of an application and then sent together when (e.g.) an exception occurs.
 */
public class RavenContext implements AutoCloseable {
    /**
     * The number of {@link Breadcrumb}s to keep in the ring buffer by default.
     */
    private static final int DEFAULT_BREADCRUMB_LIMIT = 100;

    /**
     * Thread local set of activate context objects. Note that a {@link ConcurrentHashMap}
     * is used here because no Concurrent Set exists in the Java standard library.
     * We use the same object for the Key and the Value and treat it like a Set.
     */
    private static ThreadLocal<ConcurrentHashMap<RavenContext, RavenContext>> activeContexts =
        new ThreadLocal<ConcurrentHashMap<RavenContext, RavenContext>>() {
            @Override
            protected ConcurrentHashMap<RavenContext, RavenContext> initialValue() {
                return new ConcurrentHashMap<>();
            }
    };

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
        activeContexts.get().put(this, this);
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
     * @return List of active {@link RavenContext} objects.
     */
    public static List<RavenContext> getActiveContexts() {
        Collection<RavenContext> ravenContexts = activeContexts.get().values();
        List<RavenContext> retList = new ArrayList<>(ravenContexts.size());
        retList.addAll(ravenContexts);
        return retList;
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
