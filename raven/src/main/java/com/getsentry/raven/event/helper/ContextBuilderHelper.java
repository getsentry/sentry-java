package com.getsentry.raven.event.helper;

import com.getsentry.raven.Raven;
import com.getsentry.raven.RavenContext;
import com.getsentry.raven.event.Breadcrumb;
import com.getsentry.raven.event.EventBuilder;
import com.getsentry.raven.event.User;
import com.getsentry.raven.event.interfaces.UserInterface;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * {@link EventBuilderHelper} that extracts and sends any data attached to the
 * provided {@link Raven}'s {@link com.getsentry.raven.RavenContext}.
 */
public class ContextBuilderHelper implements EventBuilderHelper {

    /**
     * Raven object where the RavenContext comes from.
     */
    private Raven raven;

    /**
     * {@link EventBuilderHelper} that extracts context data from the provided {@link Raven} client.
     *
     * @param raven Raven client which holds RavenContext to be used.
     */
    public ContextBuilderHelper(Raven raven) {
        this.raven = raven;
    }

    @Override
    public void helpBuildingEvent(EventBuilder eventBuilder) {
        List<Breadcrumb> breadcrumbs = new ArrayList<>();
        RavenContext context = raven.getContext();

        Iterator<Breadcrumb> breadcrumbIterator = context.getBreadcrumbs();
        while (breadcrumbIterator.hasNext()) {
            breadcrumbs.add(breadcrumbIterator.next());
        }
        eventBuilder.withBreadcrumbs(breadcrumbs);

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
