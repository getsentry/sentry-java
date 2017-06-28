package io.sentry.context;

import io.sentry.event.Breadcrumb;
import io.sentry.event.User;
import io.sentry.util.CircularFifoQueue;

import java.io.Serializable;
import java.util.*;

/**
 * Context is used to hold context data (such as {@link Breadcrumb}s)
 * so that data may be collected in different parts of an application and
 * then sent together when an exception occurs.
 */
public class Context implements Serializable {
    /**
     * The number of {@link Breadcrumb}s to keep in the ring buffer by default.
     */
    private static final int DEFAULT_BREADCRUMB_LIMIT = 100;

    /**
     * UUID of the last event sent to the Sentry server, if any.
     */
    private volatile UUID lastEventId;

    /**
     * Ring buffer of {@link Breadcrumb} objects.
     */
    private CircularFifoQueue<Breadcrumb> breadcrumbs;

    /**
     * User active in the current context, if any.
     */
    private volatile User user;

    /**
     * Create a new (empty) Context object with the default Breadcrumb limit.
     */
    public Context() {
        this(DEFAULT_BREADCRUMB_LIMIT);
    }

    /**
     * Create a new (empty) Context object with the specified Breadcrumb limit.
     *
     * @param breadcrumbLimit Number of Breadcrumb objects to retain in ring buffer.
     */
    public Context(int breadcrumbLimit) {
        breadcrumbs = new CircularFifoQueue<>(breadcrumbLimit);
    }

    /**
     * Clear state from this context.
     */
    public synchronized void clear() {
        breadcrumbs.clear();
        lastEventId = null;
        user = null;
    }

    /**
     * Return {@link Breadcrumb}s attached to this Context.
     *
     * @return Iterator of {@link Breadcrumb}s.
     */
    public synchronized Iterator<Breadcrumb> getBreadcrumbs() {
        List<Breadcrumb> copyList = new ArrayList<>(breadcrumbs.size());
        copyList.addAll(breadcrumbs);
        return copyList.iterator();
    }

    /**
     * Record a single {@link Breadcrumb} into this context.
     *
     * @param breadcrumb Breadcrumb object to record
     */
    public synchronized void recordBreadcrumb(Breadcrumb breadcrumb) {
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
     * cleared. For example the SentryServletRequestListener clears the thread's Context
     * at the end of each request.
     *
     * @return UUID of the last event sent by this thread.
     */
    public UUID getLastEventId() {
        return lastEventId;
    }

    /**
     * Store the current user in the context.
     *
     * @param user User to store in context.
     */
    public void setUser(User user) {
        this.user = user;
    }

    /**
     * Clears the current user set on this context.
     */
    public void clearUser() {
        setUser(null);
    }

    /**
     * Gets the current user from the context.
     *
     * @return User currently stored in context.
     */
    public User getUser() {
        return user;
    }
}
