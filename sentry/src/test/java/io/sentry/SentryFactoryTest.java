package io.sentry;

import mockit.*;
import io.sentry.dsn.Dsn;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;

import static mockit.Deencapsulation.setField;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class SentryFactoryTest {
    @Tested
    private SentryFactory sentryFactory = null;
    @Injectable
    private ServiceLoader<SentryFactory> mockServiceLoader = null;

    @BeforeMethod
    public void setUp() throws Exception {
        setField(SentryFactory.class, "AUTO_REGISTERED_FACTORIES", mockServiceLoader);

        new NonStrictExpectations() {{
            mockServiceLoader.iterator();
            result = Collections.emptyIterator();
        }};
    }

    @AfterMethod
    public void tearDown() throws Exception {
        // Reset the registered factories
        setField(SentryFactory.class, "MANUALLY_REGISTERED_FACTORIES", new HashSet<>());
        setField(SentryFactory.class, "AUTO_REGISTERED_FACTORIES", ServiceLoader.load(SentryFactory.class));
    }

    @Test
    public void testGetFactoriesFromServiceLoader(@Injectable final Sentry mockSentry,
                                                  @Injectable final Dsn mockDsn) throws Exception {
        new NonStrictExpectations() {{
            mockServiceLoader.iterator();
            result = new Delegate<Iterator<SentryFactory>>() {
                @SuppressWarnings("unused")
                public Iterator<SentryFactory> iterator() {
                    return Collections.singletonList(sentryFactory).iterator();
                }
            };
            sentryFactory.createSentryInstance(mockDsn);
            result = mockSentry;
        }};

        Sentry sentry = SentryFactory.sentryInstance(mockDsn);

        assertThat(sentry, is(mockSentry));
    }

    @Test
    public void testGetFactoriesManuallyAdded(@Injectable final Sentry mockSentry,
                                              @Injectable final Dsn mockDsn) throws Exception {
        SentryFactory.registerFactory(sentryFactory);
        new NonStrictExpectations() {{
            sentryFactory.createSentryInstance(mockDsn);
            result = mockSentry;
        }};

        Sentry sentry = SentryFactory.sentryInstance(mockDsn);

        assertThat(sentry, is(mockSentry));
    }

    @Test
    public void testSentryInstanceForFactoryNameSucceedsIfFactoryFound(@Injectable final Sentry mockSentry,
                                                                      @Injectable final Dsn mockDsn) throws Exception {
        String factoryName = sentryFactory.getClass().getName();
        SentryFactory.registerFactory(sentryFactory);
        new NonStrictExpectations() {{
            sentryFactory.createSentryInstance(mockDsn);
            result = mockSentry;
        }};

        Sentry sentry = SentryFactory.sentryInstance(mockDsn, factoryName);

        assertThat(sentry, is(mockSentry));
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testSentryInstanceForFactoryNameFailsIfNoFactoryFound(@Injectable final Sentry mockSentry,
                                                                     @Injectable final Dsn mockDsn) throws Exception {
        String factoryName = "invalidName";
        SentryFactory.registerFactory(sentryFactory);
        new NonStrictExpectations() {{
            sentryFactory.createSentryInstance(mockDsn);
            result = mockSentry;
        }};

        SentryFactory.sentryInstance(mockDsn, factoryName);
    }

    @Test
    public void testSentryInstantiationFailureCaught(@Injectable final Dsn mockDsn) throws Exception {
        SentryFactory.registerFactory(sentryFactory);
        Exception exception = null;
        new NonStrictExpectations() {{
            sentryFactory.createSentryInstance(mockDsn);
            result = new RuntimeException();
        }};

        try {
            SentryFactory.sentryInstance(mockDsn);
        } catch (IllegalStateException e) {
            exception = e;
        }

        assertThat(exception, notNullValue());
    }

    @Test
    public void testAutoDetectDsnIfNotProvided(@Injectable final Sentry mockSentry,
                                               @SuppressWarnings("unused") @Mocked final Dsn mockDsn) throws Exception {
        final String dsn = "protocol://user:password@host:port/3";
        SentryFactory.registerFactory(sentryFactory);
        new NonStrictExpectations() {{
            Dsn.dsnLookup();
            result = dsn;

            sentryFactory.createSentryInstance((Dsn) any);
            result = mockSentry;
        }};

        Sentry sentry = SentryFactory.sentryInstance();

        assertThat(sentry, is(mockSentry));
        new Verifications() {{
            new Dsn(dsn);
        }};
    }

    @Test
    public void testCreateDsnIfStringProvided(@Injectable final Sentry mockSentry,
                                              @SuppressWarnings("unused") @Mocked final Dsn mockDsn) throws Exception {
        final String dsn = "protocol://user:password@host:port/2";
        SentryFactory.registerFactory(sentryFactory);
        new NonStrictExpectations() {{
            sentryFactory.createSentryInstance((Dsn) any);
            result = mockSentry;
        }};

        Sentry sentry = SentryFactory.sentryInstance(dsn);

        assertThat(sentry, is(mockSentry));
        new Verifications() {{
            new Dsn(dsn);
        }};
    }
}
