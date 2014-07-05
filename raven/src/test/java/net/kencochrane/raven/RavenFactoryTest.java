package net.kencochrane.raven;

import com.google.common.collect.Iterators;
import mockit.*;
import net.kencochrane.raven.dsn.Dsn;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashSet;
import java.util.Iterator;
import java.util.ServiceLoader;

import static mockit.Deencapsulation.setField;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class RavenFactoryTest {
    @Tested
    private RavenFactory ravenFactory = null;
    @Injectable
    private ServiceLoader<RavenFactory> mockServiceLoader = null;

    @BeforeMethod
    public void setUp() throws Exception {
        setField(RavenFactory.class, "AUTO_REGISTERED_FACTORIES", mockServiceLoader);

        new NonStrictExpectations() {{
            mockServiceLoader.iterator();
            result = Iterators.<RavenFactory>emptyIterator();
        }};
    }

    @AfterMethod
    public void tearDown() throws Exception {
        // Reset the registered factories
        setField(RavenFactory.class, "MANUALLY_REGISTERED_FACTORIES", new HashSet<>());
        setField(RavenFactory.class, "AUTO_REGISTERED_FACTORIES", ServiceLoader.load(RavenFactory.class));
    }

    @Test
    public void testGetFactoriesFromServiceLoader(@Injectable final Raven mockRaven,
                                                  @Injectable final Dsn mockDsn) throws Exception {
        new NonStrictExpectations() {{
            mockServiceLoader.iterator();
            result = new Delegate<Iterator<RavenFactory>>() {
                @SuppressWarnings("unused")
                public Iterator<RavenFactory> iterator() {
                    return Iterators.singletonIterator(ravenFactory);
                }
            };
            ravenFactory.createRavenInstance(mockDsn);
            result = mockRaven;
        }};

        Raven raven = RavenFactory.ravenInstance(mockDsn);

        assertThat(raven, is(mockRaven));
    }

    @Test
    public void testGetFactoriesManuallyAdded(@Injectable final Raven mockRaven,
                                              @Injectable final Dsn mockDsn) throws Exception {
        RavenFactory.registerFactory(ravenFactory);
        new NonStrictExpectations() {{
            ravenFactory.createRavenInstance(mockDsn);
            result = mockRaven;
        }};

        Raven raven = RavenFactory.ravenInstance(mockDsn);

        assertThat(raven, is(mockRaven));
    }

    @Test
    public void testRavenInstanceForFactoryNameSucceedsIfFactoryFound(@Injectable final Raven mockRaven,
                                                                      @Injectable final Dsn mockDsn) throws Exception {
        String factoryName = ravenFactory.getClass().getName();
        RavenFactory.registerFactory(ravenFactory);
        new NonStrictExpectations() {{
            ravenFactory.createRavenInstance(mockDsn);
            result = mockRaven;
        }};

        Raven raven = RavenFactory.ravenInstance(mockDsn, factoryName);

        assertThat(raven, is(mockRaven));
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testRavenInstanceForFactoryNameFailsIfNoFactoryFound(@Injectable final Raven mockRaven,
                                                                     @Injectable final Dsn mockDsn) throws Exception {
        String factoryName = "invalidName";
        RavenFactory.registerFactory(ravenFactory);
        new NonStrictExpectations() {{
            ravenFactory.createRavenInstance(mockDsn);
            result = mockRaven;
        }};

        RavenFactory.ravenInstance(mockDsn, factoryName);
    }

    @Test
    public void testRavenInstantiationFailureCaught(@Injectable final Dsn mockDsn) throws Exception {
        RavenFactory.registerFactory(ravenFactory);
        Exception exception = null;
        new NonStrictExpectations() {{
            ravenFactory.createRavenInstance(mockDsn);
            result = new RuntimeException();
        }};

        try {
            RavenFactory.ravenInstance(mockDsn);
        } catch (IllegalStateException e) {
            exception = e;
        }

        assertThat(exception, notNullValue());
    }

    @Test
    public void testAutoDetectDsnIfNotProvided(@Injectable final Raven mockRaven,
                                               @SuppressWarnings("unused") @Mocked final Dsn mockDsn) throws Exception {
        final String dsn = "protocol://user:password@host:port/3";
        RavenFactory.registerFactory(ravenFactory);
        new NonStrictExpectations() {{
            Dsn.dsnLookup();
            result = dsn;

            ravenFactory.createRavenInstance((Dsn) any);
            result = mockRaven;
        }};

        Raven raven = RavenFactory.ravenInstance();

        assertThat(raven, is(mockRaven));
        new Verifications() {{
            new Dsn(dsn);
        }};
    }

    @Test
    public void testCreateDsnIfStringProvided(@Injectable final Raven mockRaven,
                                              @SuppressWarnings("unused") @Mocked final Dsn mockDsn) throws Exception {
        final String dsn = "protocol://user:password@host:port/2";
        RavenFactory.registerFactory(ravenFactory);
        new NonStrictExpectations() {{
            ravenFactory.createRavenInstance((Dsn) any);
            result = mockRaven;
        }};

        Raven raven = RavenFactory.ravenInstance(dsn);

        assertThat(raven, is(mockRaven));
        new Verifications() {{
            new Dsn(dsn);
        }};
    }
}
