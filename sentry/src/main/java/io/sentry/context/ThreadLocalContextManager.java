package io.sentry.context;

/**
 * A {@link ContextManager} that returns a unique {@link Context} instance per thread.
 */
public class ThreadLocalContextManager implements ContextManager {
    private final ThreadLocal<Context> context = new ThreadLocal<Context>() {
        @Override
        protected Context initialValue() {
            return new Context();
        }
    };

    /**
     * Returns a unique {@link Context} instance per thread. Useful for
     * applications that use a single thread to server a user's request.
     *
     * @return a unique {@link Context} instance per thread.
     */
    public Context getContext() {
        return context.get();
    }

    @Override
    public void clear() {
        context.remove();
    }

}
