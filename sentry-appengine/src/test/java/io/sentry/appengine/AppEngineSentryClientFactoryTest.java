package io.sentry.appengine;

import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.utils.SystemProperty;
import io.sentry.appengine.connection.ConnectionsInfo;
import io.sentry.config.Lookup;
import io.sentry.config.provider.ConfigurationProvider;
import io.sentry.appengine.connection.AppEngineAsyncConnection;
import io.sentry.connection.Connection;
import io.sentry.dsn.Dsn;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AppEngineSentryClientFactoryTest {
    private AppEngineSentryClientFactory appEngineSentryClientFactory;
    private Connection mockConnection;
    private ConfigurationProvider mockLookupConfig;

    @Before
    public void setup() {
        TestQueueFactoryProvider.reset();
        mockLookupConfig = mock(ConfigurationProvider.class);
        Lookup mockLookup = new Lookup(mockLookupConfig, mock(ConfigurationProvider.class));
        mockConnection = mock(Connection.class);
        appEngineSentryClientFactory = new AppEngineSentryClientFactory(mockLookup);
    }

    @Test
    public void asyncConnectionCreatedByAppEngineSentryClientFactoryIsForAppEngine() throws Exception {
        Connection connection = appEngineSentryClientFactory.createAsyncConnection(mock(Dsn.class), mockConnection);

        assertThat(connection, is(instanceOf(AppEngineAsyncConnection.class)));
    }

    @Test
    public void asyncConnectionWithoutConnectionIdGeneratesDefaultId() throws Exception {
        Dsn mockDsn = new Dsn("noop://localhost?key=lsdfjop");

        AppEngineAsyncConnection conn = (AppEngineAsyncConnection) appEngineSentryClientFactory.createAsyncConnection(mockDsn, mockConnection);

        String expectedId = AppEngineSentryClientFactory.class.getCanonicalName() + mockDsn.toString()
                + SystemProperty.version.get();

        AppEngineAsyncConnection foundConnection = ConnectionsInfo.getExistingConnectionById(expectedId);

        assertSame(conn, foundConnection);
    }

    @Test
    public void asyncConnectionWithConnectionIdUsesId() throws Exception {
        String connectionId = "543afd41-379d-41cb-8c99-8ce73e83a0cc";
        Dsn dsn = new Dsn(Dsn.DEFAULT_DSN + "?" + AppEngineSentryClientFactory.CONNECTION_IDENTIFIER + "="
                + connectionId);

        String expectedId = AppEngineSentryClientFactory.class.getCanonicalName()
                + dsn + SystemProperty.version.get();

        Connection conn = appEngineSentryClientFactory.createAsyncConnection(dsn, mockConnection);

        AppEngineAsyncConnection foundConnection = ConnectionsInfo.getExistingConnectionById(expectedId);

        assertSame(conn, foundConnection);
    }

    @Test
    public void asyncConnectionWithoutQueueNameKeepsDefaultQueue() throws Exception {
        Dsn dsn = new Dsn(Dsn.DEFAULT_DSN);
        AppEngineAsyncConnection conn = (AppEngineAsyncConnection) appEngineSentryClientFactory
                .createAsyncConnection(dsn, mockConnection);

        assertNull(conn.getQueue());
    }

    @Test
    public void asyncConnectionWithQueueNameSetsQueue() throws Exception {
        String mockQueueName = "queueName";

        Queue mockQueue = mock(Queue.class);
        when(mockQueue.getQueueName()).thenReturn(mockQueueName);

        TestQueueFactoryProvider.registerQueue(mockQueueName, mockQueue);

        when(mockLookupConfig.getProperty(eq(AppEngineSentryClientFactory.QUEUE_NAME)))
                .thenReturn(mockQueueName);

        AppEngineAsyncConnection conn = (AppEngineAsyncConnection) appEngineSentryClientFactory
                .createAsyncConnection(mock(Dsn.class), mockConnection);

        assertEquals(mockQueueName, conn.getQueue());
    }
}
