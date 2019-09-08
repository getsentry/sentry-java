package io.sentry;

import io.sentry.connection.Connection;
import io.sentry.connection.HttpConnection;
import io.sentry.connection.NoopConnection;
import io.sentry.context.ContextManager;
import io.sentry.context.SingletonContextManager;
import io.sentry.dsn.Dsn;
import io.sentry.event.Event;
import io.sentry.event.EventBuilder;
import io.sentry.event.helper.EventBuilderHelper;
import io.sentry.event.interfaces.ExceptionInterface;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class SentryTest extends BaseTest {
    private SentryClient sentryClient = null;
    private Connection mockConnection = null;
    private ContextManager contextManager = new SingletonContextManager();
    private EventBuilderHelper mockEventBuilderHelper = null;

    @Before
    public void setup() {
        contextManager.clear();
        mockConnection = mock(Connection.class);
        mockEventBuilderHelper = mock(EventBuilderHelper.class);
        sentryClient = new SentryClient(mockConnection, contextManager);
    }

    @Test
    public void testSendEventStatically() throws Exception {
        final String message = "e960981e-656d-4404-9b1d-43b483d3f32c";
        Event event = new EventBuilder().withMessage(message).withLevel(Event.Level.ERROR).build();

        Sentry.setStoredClient(sentryClient);
        Sentry.capture(event);

        ArgumentCaptor<Event> eventArgumentCaptor = ArgumentCaptor.forClass(Event.class);
        verify(mockConnection).send(eventArgumentCaptor.capture());
        event = eventArgumentCaptor.getValue();
        assertThat(event.getLevel(), equalTo(Event.Level.ERROR));
        assertThat(event.getMessage(), equalTo(message));
    }

    @Test
    public void testSendEventBuilderStatically() throws Exception {
        final String message = "e960981e-656d-4404-9b1d-43b483d3f32c";
        EventBuilder eventBuilder = new EventBuilder().withMessage(message).withLevel(Event.Level.ERROR);
        sentryClient.addBuilderHelper(mockEventBuilderHelper);

        Sentry.setStoredClient(sentryClient);
        Sentry.capture(eventBuilder);

        ArgumentCaptor<Event> eventArgumentCaptor = ArgumentCaptor.forClass(Event.class);
        verify(mockConnection).send(eventArgumentCaptor.capture());
        Event event = eventArgumentCaptor.getValue();
        assertThat(event.getLevel(), equalTo(Event.Level.ERROR));
        assertThat(event.getMessage(), equalTo(message));
    }

    @Test
    public void testSendMessageStatically() throws Exception {
        final String message = "e960981e-656d-4404-9b1d-43b483d3f32c";
        sentryClient.addBuilderHelper(mockEventBuilderHelper);

        Sentry.setStoredClient(sentryClient);
        Sentry.capture(message);

        ArgumentCaptor<Event> eventArgumentCaptor = ArgumentCaptor.forClass(Event.class);
        verify(mockEventBuilderHelper).helpBuildingEvent(any(EventBuilder.class));
        verify(mockConnection).send(eventArgumentCaptor.capture());
        Event event = eventArgumentCaptor.getValue();
        assertThat(event.getLevel(), equalTo(Event.Level.INFO));
        assertThat(event.getMessage(), equalTo(message));
    }

    @Test
    public void testSendExceptionStatically() throws Exception {
        final String message = "7b61ddb1-eb32-428d-bad9-a7d842605ba7";
        final Exception exception = new Exception(message);
        sentryClient.addBuilderHelper(mockEventBuilderHelper);

        Sentry.setStoredClient(sentryClient);
        Sentry.capture(exception);

        ArgumentCaptor<Event> eventArgumentCaptor = ArgumentCaptor.forClass(Event.class);
        verify(mockEventBuilderHelper).helpBuildingEvent(any(EventBuilder.class));
        verify(mockConnection).send(eventArgumentCaptor.capture());
        Event event = eventArgumentCaptor.getValue();
        assertThat(event.getLevel(), equalTo(Event.Level.ERROR));
        assertThat(event.getMessage(), equalTo(message));
        assertThat(event.getSentryInterfaces(), hasKey(ExceptionInterface.EXCEPTION_INTERFACE));
    }

    @Test
    public void testInitNoDsn() throws Exception {
        SentryClient sentryClient = Sentry.init();
        assertThat(sentryClient.getConnection(), instanceOf(NoopConnection.class));
    }

    @Test
    public void testInitNullDsn() throws Exception {
        SentryClient sentryClient = Sentry.init((String) null);
        assertThat(sentryClient.getConnection(), instanceOf(NoopConnection.class));
    }

    @Test
    public void testInitNullFactory() throws Exception {
        SentryClient sentryClient = Sentry.init((SentryClientFactory) null);
        assertThat(sentryClient.getConnection(), instanceOf(NoopConnection.class));
    }

    @Test
    public void testInitStringDsn() throws Exception {
        SentryClient sentryClient = Sentry.init("http://public:private@localhost:4567/1?async=false");
        Connection connection = sentryClient.getConnection();
        assertThat(connection, instanceOf(HttpConnection.class));
    }

    @Test
    public void testInitStringDsnAndFactory() throws Exception {
        SentryClient sentryClient = Sentry.init("http://public:private@localhost:4567/1?async=false",
                new DefaultSentryClientFactory());
        Connection connection = sentryClient.getConnection();
        assertThat(connection, instanceOf(HttpConnection.class));
    }

    @Test
    public void testInitProvidingFactory() throws Exception {
        final SentryClient specificInstance = new SentryClient(null, null);
        SentryClientFactory specificInstanceFactory = new SentryClientFactory() {
            @Override
            public SentryClient createSentryClient(Dsn dsn) {
                return specificInstance;
            }
        };

        SentryClient sentryClient = Sentry.init(specificInstanceFactory);
        assertThat(sentryClient, sameInstance(specificInstance));
    }
}
