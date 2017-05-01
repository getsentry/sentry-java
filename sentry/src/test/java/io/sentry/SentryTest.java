package io.sentry;

import io.sentry.connection.Connection;
import io.sentry.context.ContextManager;
import io.sentry.context.SingletonContextManager;
import io.sentry.event.Event;
import io.sentry.event.EventBuilder;
import io.sentry.event.helper.EventBuilderHelper;
import io.sentry.event.interfaces.ExceptionInterface;
import mockit.Injectable;
import mockit.Tested;
import mockit.Verifications;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;

public class SentryTest {
    @Tested
    private SentryClient sentryClient = null;
    @Injectable
    private Connection mockConnection = null;
    @Injectable
    private ContextManager contextManager = new SingletonContextManager();
    @Injectable
    private EventBuilderHelper mockEventBuilderHelper = null;

    @BeforeMethod
    public void setup() {
        contextManager.getContext().clear();
    }

    @Test
    public void testSendEventStatically() throws Exception {
        final String message = "e960981e-656d-4404-9b1d-43b483d3f32c";
        Event event = new EventBuilder().withMessage(message).withLevel(Event.Level.ERROR).build();

        Sentry.setStoredClient(sentryClient);
        Sentry.capture(event);

        new Verifications() {{
            Event event;
            mockConnection.send(event = withCapture());
            assertThat(event.getLevel(), equalTo(Event.Level.ERROR));
            assertThat(event.getMessage(), equalTo(message));
        }};
    }

    @Test
    public void testSendEventBuilderStatically() throws Exception {
        final String message = "e960981e-656d-4404-9b1d-43b483d3f32c";
        EventBuilder eventBuilder = new EventBuilder().withMessage(message).withLevel(Event.Level.ERROR);
        sentryClient.addBuilderHelper(mockEventBuilderHelper);

        Sentry.setStoredClient(sentryClient);
        Sentry.capture(eventBuilder);

        new Verifications() {{
            Event event;
            mockEventBuilderHelper.helpBuildingEvent((EventBuilder) any);
            mockConnection.send(event = withCapture());
            assertThat(event.getLevel(), equalTo(Event.Level.ERROR));
            assertThat(event.getMessage(), equalTo(message));
        }};
    }

    @Test
    public void testSendMessageStatically() throws Exception {
        final String message = "e960981e-656d-4404-9b1d-43b483d3f32c";
        sentryClient.addBuilderHelper(mockEventBuilderHelper);

        Sentry.setStoredClient(sentryClient);
        Sentry.capture(message);

        new Verifications() {{
            Event event;
            mockEventBuilderHelper.helpBuildingEvent((EventBuilder) any);
            mockConnection.send(event = withCapture());
            assertThat(event.getLevel(), equalTo(Event.Level.INFO));
            assertThat(event.getMessage(), equalTo(message));
        }};
    }

    @Test
    public void testSendExceptionStatically() throws Exception {
        final String message = "7b61ddb1-eb32-428d-bad9-a7d842605ba7";
        final Exception exception = new Exception(message);
        sentryClient.addBuilderHelper(mockEventBuilderHelper);

        Sentry.setStoredClient(sentryClient);
        Sentry.capture(exception);

        new Verifications() {{
            Event event;
            mockEventBuilderHelper.helpBuildingEvent((EventBuilder) any);
            mockConnection.send(event = withCapture());
            assertThat(event.getLevel(), equalTo(Event.Level.ERROR));
            assertThat(event.getMessage(), equalTo(message));
            assertThat(event.getSentryInterfaces(), hasKey(ExceptionInterface.EXCEPTION_INTERFACE));
        }};
    }

}
