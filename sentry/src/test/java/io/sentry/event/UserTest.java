package io.sentry.event;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.HashMap;
import java.util.Map;

import io.sentry.BaseTest;
import io.sentry.SentryClient;
import io.sentry.connection.Connection;
import io.sentry.context.ContextManager;
import io.sentry.context.SingletonContextManager;
import io.sentry.event.helper.ContextBuilderHelper;
import io.sentry.event.interfaces.UserInterface;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class UserTest extends BaseTest {
    private SentryClient sentryClient = null;
    private Connection mockConnection = null;
    private ContextManager contextManager = new SingletonContextManager();

    @Before
    public void setup() {
        contextManager.clear();
        mockConnection = mock(Connection.class);
        sentryClient = new SentryClient(mockConnection, contextManager);
    }

    @Test
    public void testUserPropagation() {
        sentryClient.addBuilderHelper(new ContextBuilderHelper(sentryClient));

        final User user = new UserBuilder()
            .setEmail("test@example.com")
            .setId("1234")
            .setIpAddress("192.168.0.1")
            .setUsername("testUser_123")
            .withData("foo", "bar")
            .withData("baz", 2)
            .build();
        sentryClient.getContext().setUser(user);

        sentryClient.sendEvent(new EventBuilder()
            .withMessage("Some random message")
            .withLevel(Event.Level.INFO));

        final Map<String, Object> map = new HashMap<>();
        map.put("foo", "bar");
        map.put("baz", 2);

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);

        verify(mockConnection).send(eventCaptor.capture());
        Event event = eventCaptor.getValue();
        UserInterface userInterface = (UserInterface) event.getSentryInterfaces().get(UserInterface.USER_INTERFACE);
        assertThat(userInterface.getId(), equalTo(user.getId()));
        assertThat(userInterface.getEmail(), equalTo(user.getEmail()));
        assertThat(userInterface.getIpAddress(), equalTo(user.getIpAddress()));
        assertThat(userInterface.getUsername(), equalTo(user.getUsername()));
        assertThat(userInterface.getData(), equalTo(map));
    }

}
