package net.kencochrane.raven.appengine;

import mockit.*;
import net.kencochrane.raven.RavenFactory;
import net.kencochrane.raven.appengine.connection.AppEngineAsyncConnection;
import net.kencochrane.raven.connection.Connection;
import net.kencochrane.raven.dsn.Dsn;
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
    public void checkServiceLoaderProvidesFactory() {
        ServiceLoader<RavenFactory> ravenFactories = ServiceLoader.load(RavenFactory.class);

        assertThat(ravenFactories, Matchers.<RavenFactory>hasItem(instanceOf(AppEngineRavenFactory.class)));
    }

    @Test
    public void asyncConnectionCreatedByAppEngineRavenFactoryIsForAppEngine() {
        Connection connection = appEngineRavenFactory.createAsyncConnection(mockDsn, mockConnection);

        assertThat(connection, is(instanceOf(AppEngineAsyncConnection.class)));
    }

    @Test
    public void asyncConnectionWithoutConnectionIdGeneratesDefaultId() {
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
            @Injectable("543afd41-379d-41cb-8c99-8ce73e83a0cc") final String connectionId) {
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
            @Mocked final AppEngineAsyncConnection mockAppEngineAsyncConnection) {
        appEngineRavenFactory.createAsyncConnection(mockDsn, mockConnection);

        new Verifications() {{
            mockAppEngineAsyncConnection.setQueue(anyString);
            times = 0;
        }};
    }

    @Test
    public void asyncConnectionWithQueueNameSetsQueue(
            @Mocked final AppEngineAsyncConnection mockAppEngineAsyncConnection,
            @Injectable("queueName") final String mockQueueName) {
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
