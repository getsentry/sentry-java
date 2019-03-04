package io.sentry.context;

import io.sentry.event.Breadcrumb;
import io.sentry.event.User;
import io.sentry.event.interfaces.HttpInterface;
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
     * The number of {@link Breadcrumb}s to keep in the ring buffer.
     */
    private final int breadcrumbLimit;
    /**
     * UUID of the last event sent to the Sentry server, if any.
     */
    private volatile UUID lastEventId;
    /**
     * Ring buffer of {@link Breadcrumb} objects.
     */
    private volatile CircularFifoQueue<Breadcrumb> breadcrumbs;
    /**
     * User active in the current context, if any.
     */
    private volatile User user;
    /**
     * Tags to add to any future {@link io.sentry.event.Event}s.
     */
    private volatile Map<String, String> tags;
    /**
     * Extra data to add to any future {@link io.sentry.event.Event}s.
     */
    private volatile Map<String, Object> extra;

    /**
     * HTTP data to add to any future {@link io.sentry.event.Event}s.
     */
    private volatile HttpInterface http;


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
        this.breadcrumbLimit = breadcrumbLimit;
    }

    /**
     * Clear state from this context.
     */
    public synchronized void clear() {
        setLastEventId(null);
        clearBreadcrumbs();
        clearUser();
        clearTags();
        clearExtra();
        clearHttp();
    }

    /**
     * Return {@link Breadcrumb}s attached to this context.
     *
     * @return List of {@link Breadcrumb}s.
     */
    public synchronized List<Breadcrumb> getBreadcrumbs() {
        if (breadcrumbs == null || breadcrumbs.isEmpty()) {
            return Collections.emptyList();
        }

        List<Breadcrumb> copyList = new ArrayList<>(breadcrumbs.size());
        copyList.addAll(breadcrumbs);
        return copyList;
    }

    /**
     * Return tags attached to this context.
     *
     * @return tags attached to this context.
     */
    public synchronized Map<String, String> getTags() {
        if (tags == null || tags.isEmpty()) {
            return Collections.emptyMap();
        }

        return Collections.unmodifiableMap(tags);
    }

    /**
     * Return extra data attached to this context.
     *
     * @return extra data attached to this context.
     */
    public synchronized Map<String, Object> getExtra() {
        if (extra == null || extra.isEmpty()) {
            return Collections.emptyMap();
        }

        return Collections.unmodifiableMap(extra);
    }

    /**
     * Add tag to the current context.
     *
     * @param name tag name
     * @param value tag value
     */
    public synchronized void addTag(String name, String value) {
        if (tags == null) {
            tags = new HashMap<>();
        }

        tags.put(name, value);
    }

    /**
     * Remove a tag name and value from the current context.
     *
     * @param name tag name to remove
     */
    public synchronized void removeTag(String name) {
        if (tags == null) {
            return;
        }

        tags.remove(name);
    }

    /**
     * Clear all tags from the current context.
     */
    public synchronized void clearTags() {
        tags = null;
    }

    /**
     * Add extra data to the current context.
     *
     * @param name extra name
     * @param value extra value
     */
    public synchronized void addExtra(String name, Object value) {
        if (extra == null) {
            extra = new HashMap<>();
        }

        extra.put(name, value);
    }

    /**
     * Remove an extra data name and value from the current context.
     *
     * @param name extra name to remove
     */
    public synchronized void removeExtra(String name) {
        if (extra == null) {
            return;
        }

        extra.remove(name);
    }

    /**
     * Clear all extra data from this context.
     */
    public synchronized void clearExtra() {
        extra = null;
    }


    /**
     * Store the http information in the context.
     *
     * @param http Http data to store in context.
     */
    public synchronized void setHttp(HttpInterface http) {
        this.http = http;
    }

    /**
     * Gets the http information from the context.
     *
     * @return HttpInterface currently stored in context.
     */
    public synchronized HttpInterface getHttp() {
        return http;
    }

    /**
     * Clear the http data from this context.
     */
    public synchronized void clearHttp() {
        http = null;
    }

    /**
     * Record a single {@link Breadcrumb} into this context.
     *
     * @param breadcrumb Breadcrumb object to record
     */
    public synchronized void recordBreadcrumb(Breadcrumb breadcrumb) {
        if (breadcrumbs == null) {
            breadcrumbs = new CircularFifoQueue<>(breadcrumbLimit);
        }

        breadcrumbs.add(breadcrumb);
    }

    /**
     * Clear all breadcrumbs from this context.
     */
    public synchronized void clearBreadcrumbs() {
        breadcrumbs = null;
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
