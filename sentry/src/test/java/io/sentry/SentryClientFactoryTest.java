package io.sentry;

import mockit.*;
import io.sentry.dsn.Dsn;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.ServiceLoader;

import static mockit.Deencapsulation.setField;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class SentryClientFactoryTest {
    @Tested
    private SentryClientFactory sentryClientFactory = null;
    @Injectable
    private ServiceLoader<SentryClientFactory> mockServiceLoader = null;

    @BeforeMethod
    public void setUp() throws Exception {
        setField(SentryClientFactory.class, "AUTO_REGISTERED_FACTORIES", mockServiceLoader);

        new NonStrictExpectations() {{
            mockServiceLoader.iterator();
            result = Collections.emptyIterator();
        }};
    }

    @AfterMethod
    public void tearDown() throws Exception {
        // Reset the registered factories
        setField(SentryClientFactory.class, "MANUALLY_REGISTERED_FACTORIES", new HashSet<>());
        setField(SentryClientFactory.class, "AUTO_REGISTERED_FACTORIES", ServiceLoader.load(SentryClientFactory.class));
    }

    @Test
    public void testGetFactoriesFromServiceLoader(@Injectable final SentryClient mockSentryClient,
                                                  @Injectable final Dsn mockDsn) throws Exception {
        new NonStrictExpectations() {{
            mockServiceLoader.iterator();
            result = new Delegate<Iterator<SentryClientFactory>>() {
                @SuppressWarnings("unused")
                public Iterator<SentryClientFactory> iterator() {
                    return Collections.singletonList(sentryClientFactory).iterator();
                }
            };
            sentryClientFactory.createSentryInstance(mockDsn);
            result = mockSentryClient;
        }};

        SentryClient sentryClient = SentryClientFactory.sentryInstance(mockDsn);

        assertThat(sentryClient, is(mockSentryClient));
    }

    @Test
    public void testGetFactoriesManuallyAdded(@Injectable final SentryClient mockSentryClient,
                                              @Injectable final Dsn mockDsn) throws Exception {
        SentryClientFactory.registerFactory(sentryClientFactory);
        new NonStrictExpectations() {{
            sentryClientFactory.createSentryInstance(mockDsn);
            result = mockSentryClient;
        }};

        SentryClient sentryClient = SentryClientFactory.sentryInstance(mockDsn);

        assertThat(sentryClient, is(mockSentryClient));
    }

    @Test
    public void testSentryInstanceForFactoryNameSucceedsIfFactoryFound(@Injectable final SentryClient mockSentryClient,
                                                                      @Injectable final Dsn mockDsn) throws Exception {
        String factoryName = sentryClientFactory.getClass().getName();
        SentryClientFactory.registerFactory(sentryClientFactory);
        new NonStrictExpectations() {{
            sentryClientFactory.createSentryInstance(mockDsn);
            result = mockSentryClient;
        }};

        SentryClient sentryClient = SentryClientFactory.sentryInstance(mockDsn, factoryName);

        assertThat(sentryClient, is(mockSentryClient));
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testSentryInstanceForFactoryNameFailsIfNoFactoryFound(@Injectable final SentryClient mockSentryClient,
                                                                     @Injectable final Dsn mockDsn) throws Exception {
        String factoryName = "invalidName";
        SentryClientFactory.registerFactory(sentryClientFactory);
        new NonStrictExpectations() {{
            sentryClientFactory.createSentryInstance(mockDsn);
            result = mockSentryClient;
        }};

        SentryClientFactory.sentryInstance(mockDsn, factoryName);
    }

    @Test
    public void testSentryInstantiationFailureCaught(@Injectable final Dsn mockDsn) throws Exception {
        SentryClientFactory.registerFactory(sentryClientFactory);
        Exception exception = null;
        new NonStrictExpectations() {{
            sentryClientFactory.createSentryInstance(mockDsn);
            result = new RuntimeException();
        }};

        try {
            SentryClientFactory.sentryInstance(mockDsn);
        } catch (IllegalStateException e) {
            exception = e;
        }

        assertThat(exception, notNullValue());
    }

    @Test
    public void testAutoDetectDsnIfNotProvided(@Injectable final SentryClient mockSentryClient,
                                               @SuppressWarnings("unused") @Mocked final Dsn mockDsn) throws Exception {
        final String dsn = "protocol://user:password@host:port/3";
        SentryClientFactory.registerFactory(sentryClientFactory);
        new NonStrictExpectations() {{
            Dsn.dsnLookup();
            result = dsn;

            sentryClientFactory.createSentryInstance((Dsn) any);
            result = mockSentryClient;
        }};

        SentryClient sentryClient = SentryClientFactory.sentryInstance();

        assertThat(sentryClient, is(mockSentryClient));
        new Verifications() {{
            new Dsn(dsn);
        }};
    }

    @Test
    public void testCreateDsnIfStringProvided(@Injectable final SentryClient mockSentryClient,
                                              @SuppressWarnings("unused") @Mocked final Dsn mockDsn) throws Exception {
        final String dsn = "protocol://user:password@host:port/2";
        SentryClientFactory.registerFactory(sentryClientFactory);
        new NonStrictExpectations() {{
            sentryClientFactory.createSentryInstance((Dsn) any);
            result = mockSentryClient;
        }};

        SentryClient sentryClient = SentryClientFactory.sentryInstance(dsn);

        assertThat(sentryClient, is(mockSentryClient));
        new Verifications() {{
            new Dsn(dsn);
        }};
    }
}
