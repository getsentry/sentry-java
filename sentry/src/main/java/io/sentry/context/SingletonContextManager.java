package io.sentry.context;

/**
 * {@link ContextManager} that maintains a single {@link Context} instance
 * across the entire application.
 */
public class SingletonContextManager implements ContextManager {
    private final Context context = new Context();

    /**
     * Returns a singleton {@link Context} instance. Useful for single-user
     * applications.
     *
     * @return a singleton {@link Context} instance.
     */
    public Context getContext() {
        return context;
    }

    @Override
    public void clear() {
        context.clear();
    }
}
