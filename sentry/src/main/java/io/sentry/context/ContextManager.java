package io.sentry.context;

/**
 * Context manager implementations define how context-specific data, such as
 * {@link io.sentry.event.Breadcrumb}s are coupled to the idea of
 * a "context." What "context" means depends on the application. For example,
 * most web applications would define a single request/response cycle as a context,
 * while an Android application would define the entire application run as a
 * single context.
 */
public interface ContextManager {

    /**
     * Returns the proper {@link Context} instance for the given application context.
     *
     * @return the proper {@link Context} instance for the given application context.
     */
    Context getContext();

    /**
     * Clear the underlying context data.
     */
    void clear();

}
