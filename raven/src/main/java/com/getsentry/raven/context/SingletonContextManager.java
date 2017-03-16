package com.getsentry.raven.context;

/**
 * TODO.
 */
public class SingletonContextManager implements ContextManager {
    private final Context context = new Context();

    /**
     * TODO.
     *
     * @return TODO
     */
    public Context getContext() {
        return context;
    }
}
