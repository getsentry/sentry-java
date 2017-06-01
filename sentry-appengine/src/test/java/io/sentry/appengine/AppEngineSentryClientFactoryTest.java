package io.sentry.appengine;

import mockit.*;
import io.sentry.appengine.connection.AppEngineAsyncConnection;
import io.sentry.connection.Connection;
import io.sentry.dsn.Dsn;
import org.testng.annotations.Test;

import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

public class AppEngineSentryClientFactoryTest {
    @Tested
    private AppEngineSentryClientFactory appEngineSentryClientFactory;
    @Injectable
    private Connection mockConnection;
    @Injectable
    private Dsn mockDsn;

    @Test
    public void asyncConnectionCreatedByAppEngineSentryClientFactoryIsForAppEngine() throws Exception {
        Connection connection = appEngineSentryClientFactory.createAsyncConnection(mockDsn, mockConnection);

        assertThat(connection, is(instanceOf(AppEngineAsyncConnection.class)));
    }

    @Test
    public void asyncConnectionWithoutConnectionIdGeneratesDefaultId() throws Exception {
        final String dnsString = "a1fe25d3-bc41-4040-8aa2-484e5aae87c5";
        new NonStrictExpectations() {{
            mockDsn.toString();
            result = dnsString;
        }};

        appEngineSentryClientFactory.createAsyncConnection(mockDsn, mockConnection);

        new Verifications() {{
            String connectionId = AppEngineSentryClientFactory.class.getCanonicalName() + dnsString;
            new AppEngineAsyncConnection(connectionId, mockConnection);
        }};
    }

    @Test
    public void asyncConnectionWithConnectionIdUsesId(
            @Injectable("543afd41-379d-41cb-8c99-8ce73e83a0cc") final String connectionId) throws Exception {
        new NonStrictExpectations() {{
            mockDsn.getOptions();
            result = Collections.singletonMap(AppEngineSentryClientFactory.CONNECTION_IDENTIFIER, connectionId);
        }};

        appEngineSentryClientFactory.createAsyncConnection(mockDsn, mockConnection);

        new Verifications() {{
            new AppEngineAsyncConnection(connectionId, mockConnection);
        }};
    }

    @Test
    public void asyncConnectionWithoutQueueNameKeepsDefaultQueue(
            @Mocked final AppEngineAsyncConnection mockAppEngineAsyncConnection) throws Exception {
        appEngineSentryClientFactory.createAsyncConnection(mockDsn, mockConnection);

        new Verifications() {{
            mockAppEngineAsyncConnection.setQueue(anyString);
            times = 0;
        }};
    }

    @Test
    public void asyncConnectionWithQueueNameSetsQueue(
            @Mocked final AppEngineAsyncConnection mockAppEngineAsyncConnection,
            @Injectable("queueName") final String mockQueueName) throws Exception {
        new NonStrictExpectations() {{
            mockDsn.getOptions();
            result = Collections.singletonMap(AppEngineSentryClientFactory.QUEUE_NAME, mockQueueName);
        }};

        appEngineSentryClientFactory.createAsyncConnection(mockDsn, mockConnection);

        new Verifications() {{
            mockAppEngineAsyncConnection.setQueue(mockQueueName);
        }};
    }
}
