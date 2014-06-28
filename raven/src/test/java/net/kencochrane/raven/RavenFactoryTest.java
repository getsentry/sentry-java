package net.kencochrane.raven;

import com.google.common.collect.Iterators;
import mockit.Delegate;
import mockit.Injectable;
import mockit.NonStrictExpectations;
import mockit.Tested;
import net.kencochrane.raven.dsn.Dsn;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashSet;
import java.util.Iterator;
import java.util.ServiceLoader;

import static mockit.Deencapsulation.setField;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class RavenFactoryTest {
    @Tested
    private RavenFactory ravenFactory;
    @Injectable
    private ServiceLoader mockServiceLoader;

    @BeforeMethod
    public void setUp() throws Exception {
        setField(RavenFactory.class, "AUTO_REGISTERED_FACTORIES", mockServiceLoader);

        new NonStrictExpectations() {{
            mockServiceLoader.iterator();
            result = Iterators.emptyIterator();
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
}
