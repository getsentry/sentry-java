package io.sentry.event.helper;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.isIn;
import static org.mockito.Mockito.mock;

import java.util.HashMap;
import java.util.Map;

import io.sentry.SentryClient;
import io.sentry.connection.Connection;
import io.sentry.context.Context;
import io.sentry.context.ContextManager;
import io.sentry.context.SingletonContextManager;
import io.sentry.event.Breadcrumb;
import io.sentry.event.BreadcrumbBuilder;
import io.sentry.event.Event;
import io.sentry.event.EventBuilder;
import io.sentry.event.User;
import io.sentry.event.UserBuilder;
import io.sentry.event.interfaces.UserInterface;
import org.junit.Test;

public class ContextBuilderHelperTest {
    private ContextManager contextManager = new SingletonContextManager();
    private SentryClient client = new SentryClient(mock(Connection.class), contextManager);

    @Test
    public void testHelper() {
        Context context = client.getContext();

        final Breadcrumb breadcrumb1 = new BreadcrumbBuilder().setMessage("message1").build();
        client.getContext().recordBreadcrumb(breadcrumb1);
        final Breadcrumb breadcrumb2 = new BreadcrumbBuilder().setMessage("message2").build();
        client.getContext().recordBreadcrumb(breadcrumb2);

        final User user = new UserBuilder().setEmail("email").build();
        context.setUser(user);

        context.addExtra("extra1", "value1");
        context.addExtra("extra2", 2);

        context.addTag("tag1", "value1");
        context.addTag("tag2", "value2");

        ContextBuilderHelper contextBuilderHelper = new ContextBuilderHelper(client);
        EventBuilder eventBuilder = new EventBuilder()
            .withMessage("event message")
            .withLevel(Event.Level.INFO);
        contextBuilderHelper.helpBuildingEvent(eventBuilder);

        final Event event = eventBuilder.getEvent();
        final UserInterface userInterface = (UserInterface) event.getSentryInterfaces().get(UserInterface.USER_INTERFACE);

        final Map<String, Object> extra = new HashMap<>();
        extra.put("extra1", "value1");
        extra.put("extra2", 2);

        final Map<String, String> tags = new HashMap<>();
        tags.put("tag1", "value1");
        tags.put("tag2", "value2");

        assertThat(event.getBreadcrumbs(), contains(breadcrumb1, breadcrumb2));
        assertThat(userInterface.getEmail(), equalTo(user.getEmail()));
        assertThat(event.getExtra().entrySet(), everyItem(isIn(extra.entrySet())));
        assertThat(event.getTags().entrySet(), everyItem(isIn(tags.entrySet())));
    }
}
