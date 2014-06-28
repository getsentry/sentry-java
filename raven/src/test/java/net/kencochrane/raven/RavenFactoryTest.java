package net.kencochrane.raven;

import com.google.common.collect.Iterators;
import mockit.Injectable;
import mockit.Mocked;
import mockit.NonStrictExpectations;
import mockit.Tested;
import net.kencochrane.raven.dsn.Dsn;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ServiceLoader;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class RavenFactoryTest {
    @Tested
    private RavenFactory ravenFactory;
    @Mocked
    private ServiceLoader mockServiceLoader;

    @BeforeMethod
    public void setUp() throws Exception {
        new NonStrictExpectations() {{
            ServiceLoader.load(RavenFactory.class);
            result = mockServiceLoader;
        }};
    }

    @Test
    public void testGetFactoriesFromServiceLoader(@Injectable final Raven mockRaven,
                                                  @Injectable final Dsn mockDsn) throws Exception {
        new NonStrictExpectations() {{
            mockServiceLoader.iterator();
            result = Iterators.singletonIterator(ravenFactory);
            ravenFactory.createRavenInstance(mockDsn);
            result = mockRaven;
        }};

        Raven raven = RavenFactory.ravenInstance(mockDsn);

        assertThat(raven, is(mockRaven));
    }

    @Test
    public void testGetFactoriesManuallyAdded(@Injectable final Raven mockRaven,
                                                  @Injectable final Dsn mockDsn) throws Exception {
        new NonStrictExpectations() {{
            RavenFactory.registerFactory(ravenFactory);
            ravenFactory.createRavenInstance(mockDsn);
            result = mockRaven;
        }};

        Raven raven = RavenFactory.ravenInstance(mockDsn);

        assertThat(raven, is(mockRaven));
    }
}
