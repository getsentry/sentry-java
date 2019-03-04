package io.sentry.event.helper;

import io.sentry.SentryClient;
import io.sentry.context.Context;
import io.sentry.event.Breadcrumb;
import io.sentry.event.EventBuilder;
import io.sentry.event.User;
import io.sentry.event.interfaces.UserInterface;

import java.util.List;
import java.util.Map;

/**
 * {@link EventBuilderHelper} that extracts and sends any data attached to the
 * provided {@link SentryClient}'s {@link Context}.
 */
public class ContextBuilderHelper implements EventBuilderHelper {

    /**
     * Sentry object where the Context comes from.
     */
    private SentryClient sentryClient;

    /**
     * {@link EventBuilderHelper} that extracts context data from the provided {@link SentryClient} client.
     *
     * @param sentryClient Sentry client which holds Context to be used.
     */
    public ContextBuilderHelper(SentryClient sentryClient) {
        this.sentryClient = sentryClient;
    }

    @Override
    public void helpBuildingEvent(EventBuilder eventBuilder) {
        Context context = sentryClient.getContext();

        List<Breadcrumb> breadcrumbs = context.getBreadcrumbs();
        if (!breadcrumbs.isEmpty()) {
            eventBuilder.withBreadcrumbs(breadcrumbs);
        }

        if (context.getHttp() != null) {
            eventBuilder.withSentryInterface(context.getHttp());
        }

        if (context.getUser() != null) {
            eventBuilder.withSentryInterface(fromUser(context.getUser()));
        }

        Map<String, String> tags = context.getTags();
        if (!tags.isEmpty()) {
            for (Map.Entry<String, String> entry : tags.entrySet()) {
                eventBuilder.withTag(entry.getKey(), entry.getValue());
            }
        }

        Map<String, Object> extra = context.getExtra();
        if (!extra.isEmpty()) {
            for (Map.Entry<String, Object> entry : extra.entrySet()) {
                eventBuilder.withExtra(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Builds a {@link UserInterface} object from a {@link User} object.
     * @param user User
     * @return UserInterface
     */
    private UserInterface fromUser(User user) {
        return new UserInterface(user.getId(), user.getUsername(), user.getIpAddress(),
            user.getEmail(), user.getData());
    }

}
