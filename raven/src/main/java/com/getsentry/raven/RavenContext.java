package com.getsentry.raven;

import com.getsentry.raven.event.Breadcrumb;
import com.getsentry.raven.util.CircularFifoQueue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * RavenContext is used to hold {@link ThreadLocal} context data (such as
 * {@link Breadcrumb}s) so that data may be collected in different parts
 * of an application and then sent together when an exception occurs.
 */
public class RavenContext {
    /**
     * The number of {@link Breadcrumb}s to keep in the ring buffer by default.
     */
    private static final int DEFAULT_BREADCRUMB_LIMIT = 100;

    private UUID lastEventId;

    /**
     * Ring buffer of {@link Breadcrumb} objects.
     */
    private CircularFifoQueue<Breadcrumb> breadcrumbs;

    /**
     * Create a new (empty) RavenContext object with the default Breadcrumb limit.
     */
    public RavenContext() {
        this(DEFAULT_BREADCRUMB_LIMIT);
    }

    /**
     * Create a new (empty) RavenContext object with the specified Breadcrumb limit.
     *
     * @param breadcrumbLimit Number of Breadcrumb objects to retain in ring buffer.
     */
    public RavenContext(int breadcrumbLimit) {
        breadcrumbs = new CircularFifoQueue<>(breadcrumbLimit);
    }

    /**
     * Clear state from this context.
     */
    public void clear() {
        breadcrumbs.clear();
        lastEventId = null;
    }

    /**
     * Return {@link Breadcrumb}s attached to this RavenContext.
     *
     * @return Iterator of {@link Breadcrumb}s.
     */
    public Iterator<Breadcrumb> getBreadcrumbs() {
        return breadcrumbs.iterator();
    }

    /**
     * Record a single {@link Breadcrumb} into this context.
     *
     * @param breadcrumb Breadcrumb object to record
     */
    public void recordBreadcrumb(Breadcrumb breadcrumb) {
        breadcrumbs.add(breadcrumb);
    }

    /**
     * Store the UUID of the last sent event by this thread, useful for handling user feedback.
     *
     * @param id UUID of the last event sent by this thread.
     */
    public void setLastEventId(UUID id) {
        lastEventId = id;
    }

    /**
     * Get the UUID of the last event sent by this thread, useful for handling user feedback.
     *
     * <b>Returns null</b> if no event has been sent by this thread or if the event has been
     * cleared. For example the RavenServletRequestListener clears the thread's RavenContext
     * at the end of each request.
     *
     * @return UUID of the last event sent by this thread.
     */
    public UUID getLastEventId() {
        return lastEventId;
    }
}
