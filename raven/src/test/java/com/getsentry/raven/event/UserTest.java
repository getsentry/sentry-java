package com.getsentry.raven.event;

import com.getsentry.raven.Raven;
import com.getsentry.raven.connection.Connection;
import com.getsentry.raven.event.helper.ContextBuilderHelper;
import com.getsentry.raven.event.interfaces.UserInterface;
import mockit.Injectable;
import mockit.Tested;
import mockit.Verifications;
import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;

public class UserTest {
    @Tested
    private Raven raven = null;

    @Injectable
    private Connection mockConnection = null;

    @Test
    public void testUserPropagation() {
        raven.addBuilderHelper(new ContextBuilderHelper(raven));

        final User user = new UserBuilder()
            .setEmail("test@example.com")
            .setId("1234")
            .setIpAddress("192.168.0.1")
            .setUsername("testUser_123").build();
        raven.getContext().setUser(user);

        raven.sendEvent(new EventBuilder()
            .withMessage("Some random message")
            .withLevel(Event.Level.INFO));

        new Verifications() {{
            Event event;
            mockConnection.send(event = withCapture());
            UserInterface userInterface = (UserInterface) event.getSentryInterfaces().get(UserInterface.USER_INTERFACE);
            assertThat(userInterface.getId(), equalTo(user.getId()));
            assertThat(userInterface.getEmail(), equalTo(user.getEmail()));
            assertThat(userInterface.getIpAddress(), equalTo(user.getIpAddress()));
            assertThat(userInterface.getUsername(), equalTo(user.getUsername()));
        }};
    }

}
