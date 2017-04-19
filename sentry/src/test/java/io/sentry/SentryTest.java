package io.sentry;

import io.sentry.context.ContextManager;
import io.sentry.context.SingletonContextManager;
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class SentryTest {
    @Tested
    private Sentry sentry = null;
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
        contextManager.getContext().clear();
    }

    @Test
    public void testSendEvent() throws Exception {
        sentry.sendEvent(mockEvent);

        new Verifications() {{
            mockConnection.send(mockEvent);
        }};
    }

    @Test
    public void testSendEventBuilder() throws Exception {
        final String message = "e960981e-656d-4404-9b1d-43b483d3f32c";
        sentry.addBuilderHelper(mockEventBuilderHelper);

        sentry.sendEvent(new EventBuilder()
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

        sentry.sendEvent(mockEvent);

        new Verifications() {{
            mockConnection.send(mockEvent);
        }};
    }

    @Test
    public void testSendMessage() throws Exception {
        final String message = "e960981e-656d-4404-9b1d-43b483d3f32c";
        sentry.addBuilderHelper(mockEventBuilderHelper);

        sentry.sendMessage(message);

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
        sentry.addBuilderHelper(mockEventBuilderHelper);

        sentry.sendException(exception);

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
        assertThat(sentry.getBuilderHelpers(), not(contains(mockBuilderHelper)));

        sentry.addBuilderHelper(mockBuilderHelper);
        assertThat(sentry.getBuilderHelpers(), contains(mockBuilderHelper));
        sentry.removeBuilderHelper(mockBuilderHelper);
        assertThat(sentry.getBuilderHelpers(), not(contains(mockBuilderHelper)));
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testCantModifyBuilderHelpersDirectly(@Injectable final EventBuilderHelper mockBuilderHelper) throws Exception {
        sentry.getBuilderHelpers().add(mockBuilderHelper);
    }

    @Test
    public void testRunBuilderHelpers(@Injectable final EventBuilderHelper mockBuilderHelper,
                                      @Injectable final EventBuilder mockEventBuilder) throws Exception {
        sentry.addBuilderHelper(mockBuilderHelper);

        sentry.runBuilderHelpers(mockEventBuilder);

        new Verifications() {{
            mockBuilderHelper.helpBuildingEvent(mockEventBuilder);
        }};
    }

    @Test
    public void testCloseConnectionSuccessful() throws Exception {
        sentry.closeConnection();

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

        sentry.closeConnection();
    }

    @Test
    public void testSendEventStatically() throws Exception {
        final String message = "e960981e-656d-4404-9b1d-43b483d3f32c";
        Event event = new EventBuilder().withMessage(message).withLevel(Event.Level.ERROR).build();

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
        sentry.addBuilderHelper(mockEventBuilderHelper);

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
        sentry.addBuilderHelper(mockEventBuilderHelper);

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
        sentry.addBuilderHelper(mockEventBuilderHelper);

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
