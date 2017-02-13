package com.getsentry.raven;
import com.getsentry.raven.event.Breadcrumb;
import com.getsentry.raven.event.User;
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
public class RavenContext implements AutoCloseable {
    /**
     * Thread local set of active context objects. Note that an {@link IdentityHashMap}
     * is used instead of a Set because there is no identity-set in the Java
     * standard library.
     *
     * A set of active contexts is required in order to support running multiple Raven
     * clients within a single process. In *most* cases this set will contain a single
     * active context object.
     *
     * This must be static and {@link ThreadLocal} so that users can retrieve any active
     * context objects globally, without passing context objects all the way down their
     * stacks. See {@link com.getsentry.raven.event.Breadcrumbs} for an example of how this may be used.
     */
    private static ThreadLocal<IdentityHashMap<RavenContext, RavenContext>> activeContexts =
        new ThreadLocal<IdentityHashMap<RavenContext, RavenContext>>() {
            @Override
            protected IdentityHashMap<RavenContext, RavenContext> initialValue() {
                return new IdentityHashMap<>();
            }
        };

    /**
     * The number of {@link Breadcrumb}s to keep in the ring buffer by default.
     */
    private static final int DEFAULT_BREADCRUMB_LIMIT = 100;

    /**
     * UUID of the last event sent to the Sentry server, if any.
     */
    private UUID lastEventId;

    /**
     * Ring buffer of {@link Breadcrumb} objects.
     */
    private CircularFifoQueue<Breadcrumb> breadcrumbs;

    /**
     * User active in the current context, if any.
     */
    private User user;

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
     * Add this context to the active contexts for this thread.
     */
    public void activate() {
        activeContexts.get().put(this, this);
    }

    /**
     * Remove this context from the active contexts for this thread.
     */
    public void deactivate() {
        activeContexts.get().remove(this);
    }

    /**
     * Clear state from this context.
     */
    public void clear() {
        breadcrumbs.clear();
        lastEventId = null;
        user = null;
    }

    /**
     * Calls deactivate, used by try-with-resources ({@link AutoCloseable}).
     */
    @Override
    public void close() {
        deactivate();
    }

    /**
     * Returns all active contexts for the current thread.
     *
     * @return List of active {@link RavenContext} objects.
     */
    public static List<RavenContext> getActiveContexts() {
        Collection<RavenContext> ravenContexts = activeContexts.get().values();
        List<RavenContext> list = new ArrayList<>(ravenContexts.size());
        list.addAll(ravenContexts);
        return list;
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
