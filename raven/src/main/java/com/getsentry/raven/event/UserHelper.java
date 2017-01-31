package com.getsentry.raven.event;

import com.getsentry.raven.RavenContext;

/**
 * Helpers for dealing with {@link User}.
 */
public final class UserHelper {
    /**
     * Private constructor because this is a utility class.
     */
    private UserHelper() {

    }


    /**
     * Set a {@link User} into all of this thread's active contexts.
     *
     * @param user Breadcrumb to record
     */
    public static void set(User user) {
        for (RavenContext context : RavenContext.getActiveContexts()) {
            context.setUser(user);
        }
    }

    /**
     * Clear {@link User} from all of this thread's active contexts.
     */
    public static void clear() {
        for (RavenContext context : RavenContext.getActiveContexts()) {
            context.clearUser();
        }
    }


}
