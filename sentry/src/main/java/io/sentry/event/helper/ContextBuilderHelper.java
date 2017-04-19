package io.sentry.event.helper;

import io.sentry.Sentry;
import io.sentry.context.Context;
import io.sentry.event.Breadcrumb;
import io.sentry.event.EventBuilder;
import io.sentry.event.User;
import io.sentry.event.interfaces.UserInterface;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * {@link EventBuilderHelper} that extracts and sends any data attached to the
 * provided {@link Sentry}'s {@link Context}.
 */
public class ContextBuilderHelper implements EventBuilderHelper {

    /**
     * Sentry object where the Context comes from.
     */
    private Sentry sentry;

    /**
     * {@link EventBuilderHelper} that extracts context data from the provided {@link Sentry} client.
     *
     * @param sentry Sentry client which holds Context to be used.
     */
    public ContextBuilderHelper(Sentry sentry) {
        this.sentry = sentry;
    }

    @Override
    public void helpBuildingEvent(EventBuilder eventBuilder) {
        List<Breadcrumb> breadcrumbs = new ArrayList<>();
        Context context = sentry.getContext();

        Iterator<Breadcrumb> breadcrumbIterator = context.getBreadcrumbs();
        while (breadcrumbIterator.hasNext()) {
            breadcrumbs.add(breadcrumbIterator.next());
        }

        if (!breadcrumbs.isEmpty()) {
            eventBuilder.withBreadcrumbs(breadcrumbs);
        }

        if (context.getUser() != null) {
            eventBuilder.withSentryInterface(fromUser(context.getUser()));
        }
    }

    /**
     * Builds a {@link UserInterface} object from a {@link User} object.
     * @param user User
     * @return UserInterface
     */
    private UserInterface fromUser(User user) {
        return new UserInterface(user.getId(), user.getUsername(), user.getIpAddress(), user.getEmail());
    }

}
