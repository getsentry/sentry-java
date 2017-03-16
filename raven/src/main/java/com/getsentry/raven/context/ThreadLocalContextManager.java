package com.getsentry.raven.context;

/**
 * TODO.
 */
public class ThreadLocalContextManager implements ContextManager {
    private final ThreadLocal<Context> context = new ThreadLocal<Context>() {
        @Override
        protected Context initialValue() {
            return new Context();
        }
    };

    /**
     * TODO.
     *
     * @return TODO
     */
    public Context getContext() {
        return context.get();
    }

}
