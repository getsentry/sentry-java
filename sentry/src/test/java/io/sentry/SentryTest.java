package io.sentry;

import io.sentry.connection.Connection;
import io.sentry.connection.HttpConnection;
import io.sentry.connection.NoopConnection;
import io.sentry.context.ContextManager;
import io.sentry.context.SingletonContextManager;
import io.sentry.dsn.Dsn;
import io.sentry.environment.Version;
import io.sentry.event.Event;
import io.sentry.event.EventBuilder;
import io.sentry.event.helper.EventBuilderHelper;
import io.sentry.event.interfaces.ExceptionInterface;
import mockit.Injectable;
import mockit.Tested;
import mockit.Verifications;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.net.URL;

import static org.hamcrest.MatcherAssert.assertThat;
import static mockit.Deencapsulation.getField;
import static org.hamcrest.Matchers.*;

public class SentryTest extends BaseTest {
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
        contextManager.clear();
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

    @Test
    public void testInitNoDsn() throws Exception {
        SentryClient sentryClient = Sentry.init();
        Object connection = getField(sentryClient, "connection");
        assertThat(connection, instanceOf(NoopConnection.class));
    }

    @Test
    public void testInitNullDsn() throws Exception {
        SentryClient sentryClient = Sentry.init((String) null);
        NoopConnection connection = getField(sentryClient, "connection");
        assertThat(connection, instanceOf(NoopConnection.class));
    }

    @Test
    public void testInitNullFactory() throws Exception {
        SentryClient sentryClient = Sentry.init((SentryClientFactory) null);
        NoopConnection connection = getField(sentryClient, "connection");
        assertThat(connection, instanceOf(NoopConnection.class));
    }

    @Test
    public void testInitStringDsn() throws Exception {
        SentryClient sentryClient = Sentry.init("http://public:private@localhost:4567/1?async=false");
        HttpConnection connection = getField(sentryClient, "connection");
        assertThat(connection, instanceOf(HttpConnection.class));

        URL sentryUrl = getField(connection, "sentryUrl");
        assertThat(sentryUrl.getHost(), equalTo("localhost"));
        assertThat(sentryUrl.getProtocol(), equalTo("http"));
        assertThat(sentryUrl.getPort(), equalTo(4567));
        assertThat(sentryUrl.getPath(), equalTo("/api/1/store/"));

        String authHeader = getField(connection, "authHeader");
        assertThat(authHeader, equalTo("Sentry sentry_version=6,sentry_client=sentry-java/" + Version.SDK_VERSION + ",sentry_key=public,sentry_secret=private"));
    }

    @Test
    public void testInitStringDsnAndFactory() throws Exception {
        SentryClient sentryClient = Sentry.init("http://public:private@localhost:4567/1?async=false", new DefaultSentryClientFactory());
        HttpConnection connection = getField(sentryClient, "connection");
        assertThat(connection, instanceOf(HttpConnection.class));

        URL sentryUrl = getField(connection, "sentryUrl");
        assertThat(sentryUrl.getHost(), equalTo("localhost"));
        assertThat(sentryUrl.getProtocol(), equalTo("http"));
        assertThat(sentryUrl.getPort(), equalTo(4567));
        assertThat(sentryUrl.getPath(), equalTo("/api/1/store/"));

        String authHeader = getField(connection, "authHeader");
        assertThat(authHeader, equalTo("Sentry sentry_version=6,sentry_client=sentry-java/" + Version.SDK_VERSION + ",sentry_key=public,sentry_secret=private"));
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
