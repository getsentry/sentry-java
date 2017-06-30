package io.sentry;

import io.sentry.context.ContextManager;
import io.sentry.context.SingletonContextManager;
import io.sentry.event.helper.ShouldSendEventCallback;
import mockit.Injectable;
import mockit.NonStrictExpectations;
import mockit.Tested;
import mockit.Verifications;
import io.sentry.connection.Connection;
import io.sentry.event.Event;
import io.sentry.event.EventBuilder;
import io.sentry.event.helper.EventBuilderHelper;
import io.sentry.event.interfaces.ExceptionInterface;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class SentryClientTest extends BaseTest {
    @Tested
    private SentryClient sentryClient = null;
    @Injectable
    private Connection mockConnection = null;
    @Injectable
    private ContextManager contextManager = new SingletonContextManager();
    @Injectable
    private Event mockEvent = null;
    @Injectable
    private EventBuilderHelper mockEventBuilderHelper = null;

    @BeforeMethod
    public void setup() {
        contextManager.clear();
    }

    @Test
    public void testSendEvent() throws Exception {
        sentryClient.sendEvent(mockEvent);

        new Verifications() {{
            mockConnection.send(mockEvent);
        }};
    }

    @Test
    public void testSendEventBuilder() throws Exception {
        final String message = "e960981e-656d-4404-9b1d-43b483d3f32c";
        sentryClient.addBuilderHelper(mockEventBuilderHelper);

        sentryClient.sendEvent(new EventBuilder()
            .withMessage(message)
            .withLevel(Event.Level.INFO));

        new Verifications() {{
            Event event;
            mockEventBuilderHelper.helpBuildingEvent((EventBuilder) any);
            mockConnection.send(event = withCapture());
            assertThat(event.getLevel(), equalTo(Event.Level.INFO));
            assertThat(event.getMessage(), equalTo(message));
        }};
    }

    @Test
    public void testSendEventFailingIsCaught() throws Exception {
        new NonStrictExpectations() {{
            mockConnection.send((Event) any);
            result = new RuntimeException();
        }};

        sentryClient.sendEvent(mockEvent);

        new Verifications() {{
            mockConnection.send(mockEvent);
        }};
    }

    @Test
    public void testSendMessage() throws Exception {
        final String message = "e960981e-656d-4404-9b1d-43b483d3f32c";
        sentryClient.addBuilderHelper(mockEventBuilderHelper);

        sentryClient.sendMessage(message);

        new Verifications() {{
            Event event;
            mockEventBuilderHelper.helpBuildingEvent((EventBuilder) any);
            mockConnection.send(event = withCapture());
            assertThat(event.getLevel(), equalTo(Event.Level.INFO));
            assertThat(event.getMessage(), equalTo(message));
        }};
    }

    @Test
    public void testSendException() throws Exception {
        final String message = "7b61ddb1-eb32-428d-bad9-a7d842605ba7";
        final Exception exception = new Exception(message);
        sentryClient.addBuilderHelper(mockEventBuilderHelper);

        sentryClient.sendException(exception);

        new Verifications() {{
            Event event;
            mockEventBuilderHelper.helpBuildingEvent((EventBuilder) any);
            mockConnection.send(event = withCapture());
            assertThat(event.getLevel(), equalTo(Event.Level.ERROR));
            assertThat(event.getMessage(), equalTo(message));
            assertThat(event.getSentryInterfaces(), hasKey(ExceptionInterface.EXCEPTION_INTERFACE));
        }};
    }

    @Test
    public void testAddRemoveBuilderHelpers(@Injectable final EventBuilderHelper mockBuilderHelper) throws Exception {
        assertThat(sentryClient.getBuilderHelpers(), not(contains(mockBuilderHelper)));

        sentryClient.addBuilderHelper(mockBuilderHelper);
        assertThat(sentryClient.getBuilderHelpers(), contains(mockBuilderHelper));
        sentryClient.removeBuilderHelper(mockBuilderHelper);
        assertThat(sentryClient.getBuilderHelpers(), not(contains(mockBuilderHelper)));
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testCantModifyBuilderHelpersDirectly(@Injectable final EventBuilderHelper mockBuilderHelper) throws Exception {
        sentryClient.getBuilderHelpers().add(mockBuilderHelper);
    }

    @Test
    public void testRunBuilderHelpers(@Injectable final EventBuilderHelper mockBuilderHelper,
                                      @Injectable final EventBuilder mockEventBuilder) throws Exception {
        sentryClient.addBuilderHelper(mockBuilderHelper);

        sentryClient.runBuilderHelpers(mockEventBuilder);

        new Verifications() {{
            mockBuilderHelper.helpBuildingEvent(mockEventBuilder);
        }};
    }

    @Test
    public void testCloseConnectionSuccessful() throws Exception {
        sentryClient.closeConnection();

        new Verifications() {{
            mockConnection.close();
        }};
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void testCloseConnectionFailed() throws Exception {
        new NonStrictExpectations() {{
            mockConnection.close();
            result = new IOException();
        }};

        sentryClient.closeConnection();
    }

    @Test
    public void testFields() throws Exception {
        sentryClient.addBuilderHelper(mockEventBuilderHelper);

        final String serverName = "serverName";
        sentryClient.setServerName(serverName);
        final String environment = "environment";
        sentryClient.setEnvironment(environment);
        final String dist = "dist";
        sentryClient.setDist(dist);
        final String release = "release";
        sentryClient.setRelease(release);
        final Map<String, String> tags = new HashMap<>();
        tags.put("name", "value");
        sentryClient.setTags(tags);
        final Map<String, Object> extras = new HashMap<>();
        extras.put("name", "value");
        sentryClient.setExtra(extras);

        sentryClient.sendMessage("message");

        new Verifications() {{
            Event event;
            mockEventBuilderHelper.helpBuildingEvent((EventBuilder) any);
            mockConnection.send(event = withCapture());
            assertThat(event.getLevel(), equalTo(Event.Level.INFO));
            assertThat(event.getServerName(), equalTo(serverName));
            assertThat(event.getEnvironment(), equalTo(environment));
            assertThat(event.getDist(), equalTo(dist));
            assertThat(event.getRelease(), equalTo(release));
            assertThat(event.getTags(), equalTo(tags));
            assertThat(event.getExtra(), equalTo(extras));
        }};
    }

    @Test
    public void testShouldNotSendEvent() throws Exception {
        final AtomicBoolean called = new AtomicBoolean(false);
        sentryClient.addShouldSendEventCallback(new ShouldSendEventCallback() {
            @Override
            public boolean shouldSend(Event event) {
                called.set(true);
                return false;
            }
        });

        sentryClient.sendEvent(mockEvent);

        new Verifications() {{
            mockConnection.send(mockEvent); times = 0;
            assertThat(called.get(), is(true));
        }};
    }

    @Test
    public void testShouldSendEvent() throws Exception {
        final AtomicBoolean called = new AtomicBoolean(false);
        sentryClient.addShouldSendEventCallback(new ShouldSendEventCallback() {
            @Override
            public boolean shouldSend(Event event) {
                called.set(true);
                return true;
            }
        });

        sentryClient.sendEvent(mockEvent);

        new Verifications() {{
            mockConnection.send(mockEvent);
            assertThat(called.get(), is(true));
        }};
    }
}
