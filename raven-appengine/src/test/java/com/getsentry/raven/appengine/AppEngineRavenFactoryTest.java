package com.getsentry.raven.appengine;

import mockit.*;
import com.getsentry.raven.RavenFactory;
import com.getsentry.raven.appengine.connection.AppEngineAsyncConnection;
import com.getsentry.raven.connection.Connection;
import com.getsentry.raven.dsn.Dsn;
import org.hamcrest.Matchers;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.ServiceLoader;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

public class AppEngineRavenFactoryTest {
    @Tested
    private AppEngineRavenFactory appEngineRavenFactory;
    @Injectable
    private Connection mockConnection;
    @Injectable
    private Dsn mockDsn;

    @Test
    public void checkServiceLoaderProvidesFactory() throws Exception {
        ServiceLoader<RavenFactory> ravenFactories = ServiceLoader.load(RavenFactory.class);

        assertThat(ravenFactories, Matchers.<RavenFactory>hasItem(instanceOf(AppEngineRavenFactory.class)));
    }

    @Test
    public void asyncConnectionCreatedByAppEngineRavenFactoryIsForAppEngine() throws Exception {
        Connection connection = appEngineRavenFactory.createAsyncConnection(mockDsn, mockConnection);

        assertThat(connection, is(instanceOf(AppEngineAsyncConnection.class)));
    }

    @Test
    public void asyncConnectionWithoutConnectionIdGeneratesDefaultId() throws Exception {
        final String dnsString = "a1fe25d3-bc41-4040-8aa2-484e5aae87c5";
        new NonStrictExpectations() {{
            mockDsn.toString();
            result = dnsString;
        }};

        appEngineRavenFactory.createAsyncConnection(mockDsn, mockConnection);

        new Verifications() {{
            String connectionId = AppEngineRavenFactory.class.getCanonicalName() + dnsString;
            new AppEngineAsyncConnection(connectionId, mockConnection);
        }};
    }

    @Test
    public void asyncConnectionWithConnectionIdUsesId(
            @Injectable("543afd41-379d-41cb-8c99-8ce73e83a0cc") final String connectionId) throws Exception {
        new NonStrictExpectations() {{
            mockDsn.getOptions();
            result = Collections.singletonMap(AppEngineRavenFactory.CONNECTION_IDENTIFIER, connectionId);
        }};

        appEngineRavenFactory.createAsyncConnection(mockDsn, mockConnection);

        new Verifications() {{
            new AppEngineAsyncConnection(connectionId, mockConnection);
        }};
    }

    @Test
    public void asyncConnectionWithoutQueueNameKeepsDefaultQueue(
            @Mocked final AppEngineAsyncConnection mockAppEngineAsyncConnection) throws Exception {
        appEngineRavenFactory.createAsyncConnection(mockDsn, mockConnection);

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
            result = Collections.singletonMap(AppEngineRavenFactory.QUEUE_NAME, mockQueueName);
        }};

        appEngineRavenFactory.createAsyncConnection(mockDsn, mockConnection);

        new Verifications() {{
            mockAppEngineAsyncConnection.setQueue(mockQueueName);
        }};
    }
}
