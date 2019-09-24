package io.sentry;

import io.sentry.context.ContextManager;
import io.sentry.context.SingletonContextManager;
import io.sentry.event.helper.ShouldSendEventCallback;
import io.sentry.connection.Connection;
import io.sentry.event.Event;
import io.sentry.event.EventBuilder;
import io.sentry.event.helper.EventBuilderHelper;
import io.sentry.event.interfaces.ExceptionInterface;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class SentryClientTest extends BaseTest {
    private SentryClient sentryClient = null;
    private Connection mockConnection = null;
    private ContextManager contextManager = new SingletonContextManager();
    private Event mockEvent = null;
    private EventBuilderHelper mockEventBuilderHelper = null;

    @Before
    public void setup() {
        contextManager.clear();
        mockConnection = mock(Connection.class);
        mockEvent = mock(Event.class);
        mockEventBuilderHelper = mock(EventBuilderHelper.class);
        sentryClient = new SentryClient(mockConnection, contextManager);
    }

    @Test
    public void testSendEvent() throws Exception {
        sentryClient.sendEvent(mockEvent);

        verify(mockConnection).send(eq(mockEvent));
    }

    @Test
    public void testSendNullEvent() throws Exception {
        // when
        sentryClient.sendEvent((Event) null);

        // then
        verify(mockConnection, never()).send(any(Event.class));
    }

    @Test
    public void testSendEventBuilder() throws Exception {
        final String message = "e960981e-656d-4404-9b1d-43b483d3f32c";
        sentryClient.addBuilderHelper(mockEventBuilderHelper);

        sentryClient.sendEvent(new EventBuilder()
            .withMessage(message)
            .withLevel(Event.Level.INFO));

        verify(mockEventBuilderHelper).helpBuildingEvent(any(EventBuilder.class));

        ArgumentCaptor<Event> eventArgumentCaptor = ArgumentCaptor.forClass(Event.class);
        verify(mockConnection).send(eventArgumentCaptor.capture());
        Event event = eventArgumentCaptor.getValue();
        assertThat(event.getLevel(), equalTo(Event.Level.INFO));
        assertThat(event.getMessage(), equalTo(message));
    }

    @Test
    public void testSendNullEventBuilder() throws Exception {
        // when
        sentryClient.sendEvent((EventBuilder) null);

        // then
        verify(mockConnection, never()).send(any(Event.class));
    }

    @Test
    public void testSendEventFailingIsCaught() throws Exception {
        doThrow(new RuntimeException()).when(mockConnection).send(eq(mockEvent));

        sentryClient.sendEvent(mockEvent);

        verify(mockConnection).send(eq(mockEvent));
    }

    @Test
    public void testSendMessage() throws Exception {
        final String message = "e960981e-656d-4404-9b1d-43b483d3f32c";
        sentryClient.addBuilderHelper(mockEventBuilderHelper);

        sentryClient.sendMessage(message);

        verify(mockEventBuilderHelper).helpBuildingEvent(any(EventBuilder.class));
        ArgumentCaptor<Event> eventArgumentCaptor = ArgumentCaptor.forClass(Event.class);
        verify(mockConnection).send(eventArgumentCaptor.capture());
        Event event = eventArgumentCaptor.getValue();
        assertThat(event.getLevel(), equalTo(Event.Level.INFO));
        assertThat(event.getMessage(), equalTo(message));
    }

    @Test
    public void testSendNullMessage() throws Exception {
        // when
        sentryClient.sendMessage(null);

        // then
        verify(mockConnection, never()).send(any(Event.class));
    }

    @Test
    public void testSendException() throws Exception {
        final String message = "7b61ddb1-eb32-428d-bad9-a7d842605ba7";
        final Exception exception = new Exception(message);
        sentryClient.addBuilderHelper(mockEventBuilderHelper);

        sentryClient.sendException(exception);

        verify(mockEventBuilderHelper).helpBuildingEvent(any(EventBuilder.class));
        ArgumentCaptor<Event> eventArgumentCaptor = ArgumentCaptor.forClass(Event.class);
        verify(mockConnection).send(eventArgumentCaptor.capture());
        Event event = eventArgumentCaptor.getValue();
        assertThat(event.getLevel(), equalTo(Event.Level.ERROR));
        assertThat(event.getMessage(), equalTo(message));
        assertThat(event.getSentryInterfaces(), hasKey(ExceptionInterface.EXCEPTION_INTERFACE));
    }

    @Test
    public void testSendNullException() throws Exception {
        // when
        sentryClient.sendException(null);

        // then
        verify(mockConnection, never()).send(any(Event.class));
    }

    @Test
    public void testAddRemoveBuilderHelpers() throws Exception {
        EventBuilderHelper mockBuilderHelper = mock(EventBuilderHelper.class);
        assertThat(sentryClient.getBuilderHelpers(), not(contains(mockBuilderHelper)));

        sentryClient.addBuilderHelper(mockBuilderHelper);
        assertThat(sentryClient.getBuilderHelpers(), contains(mockBuilderHelper));
        sentryClient.removeBuilderHelper(mockBuilderHelper);
        assertThat(sentryClient.getBuilderHelpers(), not(contains(mockBuilderHelper)));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testCantModifyBuilderHelpersDirectly() throws Exception {
        sentryClient.getBuilderHelpers().add(mock(EventBuilderHelper.class));
    }

    @Test
    public void testRunBuilderHelpers() throws Exception {
        EventBuilderHelper mockBuilderHelper = mock(EventBuilderHelper.class);
        EventBuilder mockEventBuilder = mock(EventBuilder.class);

        sentryClient.addBuilderHelper(mockBuilderHelper);
        sentryClient.runBuilderHelpers(mockEventBuilder);

        verify(mockBuilderHelper).helpBuildingEvent(eq(mockEventBuilder));
    }

    @Test
    public void testCloseConnectionSuccessful() throws Exception {
        sentryClient.closeConnection();
        verify(mockConnection).close();
    }

    @Test(expected = RuntimeException.class)
    public void testCloseConnectionFailed() throws Exception {
        doThrow(new IOException()).when(mockConnection).close();

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

        verify(mockEventBuilderHelper).helpBuildingEvent(any(EventBuilder.class));
        ArgumentCaptor<Event> eventArgumentCaptor = ArgumentCaptor.forClass(Event.class);
        verify(mockConnection).send(eventArgumentCaptor.capture());
        Event event = eventArgumentCaptor.getValue();
        assertThat(event.getLevel(), equalTo(Event.Level.INFO));
        assertThat(event.getServerName(), equalTo(serverName));
        assertThat(event.getEnvironment(), equalTo(environment));
        assertThat(event.getDist(), equalTo(dist));
        assertThat(event.getRelease(), equalTo(release));
        assertThat(event.getTags(), equalTo(tags));
        assertThat(event.getExtra(), equalTo(extras));
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

        verify(mockConnection, never()).send(eq(mockEvent));
        assertThat(called.get(), is(true));
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

        verify(mockConnection).send(eq(mockEvent));
        assertThat(called.get(), is(true));
    }

    @Test
    public void testDefaultTagDoesntOverrideEvent() {
        final String key = "key";
        final String expectedValue = "expected";
        sentryClient.addTag(key, "default");
        EventBuilder eventBuilder = new EventBuilder();
        eventBuilder.withTag(key, expectedValue);

        Event event = sentryClient.buildEvent(eventBuilder);

        assertThat(event.getTags().get(key), equalTo(expectedValue));
    }

    @Test
    public void testDefaultTagSetToEvent() {
        final String key = "key";
        final String expectedValue = "expected";
        sentryClient.addTag(key, expectedValue);
        EventBuilder eventBuilder = new EventBuilder();

        Event event = sentryClient.buildEvent(eventBuilder);

        assertThat(event.getTags().get(key), equalTo(expectedValue));
    }

    @Test
    public void testDefaultExtraDoesntOverrideEvent() {
        final String key = "key";
        final Object expectedValue = "expected";
        sentryClient.addExtra(key, (Object)"default");
        EventBuilder eventBuilder = new EventBuilder();
        eventBuilder.withExtra(key, expectedValue);

        Event event = sentryClient.buildEvent(eventBuilder);

        assertThat(event.getExtra().get(key), equalTo(expectedValue));
    }

    @Test
    public void testDefaultExtraSetToEvent() {
        final String key = "key";
        final Object expectedValue = "expected";
        sentryClient.addExtra(key, expectedValue);
        EventBuilder eventBuilder = new EventBuilder();

        Event event = sentryClient.buildEvent(eventBuilder);

        assertThat(event.getExtra().get(key), equalTo(expectedValue));
    }

    @Test
    public void testDefaultReleaseDoesntOverrideEvent() {
        final String expectedValue = "release";
        sentryClient.setRelease("default");
        EventBuilder eventBuilder = new EventBuilder();
        eventBuilder.withRelease(expectedValue);

        Event event = sentryClient.buildEvent(eventBuilder);

        assertThat(event.getRelease(), equalTo(expectedValue));
    }

    @Test
    public void testDefaultReleaseSetToEvent() {
        final String expectedValue = "release";
        sentryClient.setRelease(expectedValue);
        EventBuilder eventBuilder = new EventBuilder();

        Event event = sentryClient.buildEvent(eventBuilder);

        assertThat(event.getRelease(), equalTo(expectedValue));
    }

    @Test
    public void testDefaultEnvironmentDoesntOverrideEvent() {
        final String expectedValue = "env";
        sentryClient.setEnvironment("default");
        EventBuilder eventBuilder = new EventBuilder();
        eventBuilder.withEnvironment(expectedValue);

        Event event = sentryClient.buildEvent(eventBuilder);

        assertThat(event.getEnvironment(), equalTo(expectedValue));
    }

    @Test
    public void testDefaultEnvironmentSetToEvent() {
        final String expectedValue = "env";
        sentryClient.setEnvironment(expectedValue);
        EventBuilder eventBuilder = new EventBuilder();

        Event event = sentryClient.buildEvent(eventBuilder);

        assertThat(event.getEnvironment(), equalTo(expectedValue));
    }

    @Test
    public void testDefaultServerNameDoesntOverrideEvent() {
        final String expectedValue = "srv";
        sentryClient.setServerName("default");
        EventBuilder eventBuilder = new EventBuilder();
        eventBuilder.withServerName(expectedValue);

        Event event = sentryClient.buildEvent(eventBuilder);

        assertThat(event.getServerName(), equalTo(expectedValue));
    }

    @Test
    public void testDefaultServerNameSetToEvent() {
        final String expectedValue = "srv";
        sentryClient.setServerName(expectedValue);
        EventBuilder eventBuilder = new EventBuilder();

        Event event = sentryClient.buildEvent(eventBuilder);

        assertThat(event.getServerName(), equalTo(expectedValue));
    }
}
