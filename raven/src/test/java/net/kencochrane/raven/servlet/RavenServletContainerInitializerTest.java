package net.kencochrane.raven.servlet;

import mockit.Injectable;
import mockit.Tested;
import mockit.Verifications;
import org.testng.annotations.Test;

import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import java.util.ServiceLoader;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.instanceOf;

public class RavenServletContainerInitializerTest {
    @Tested
    private RavenServletContainerInitializer ravenServletContainerInitializer = null;

    @Test
    public void testInitializerInjectedViaServiceLoader() throws Exception {
        ServiceLoader<ServletContainerInitializer> serviceLoader = ServiceLoader.load(ServletContainerInitializer.class);
        assertThat(serviceLoader, contains(instanceOf(RavenServletContainerInitializer.class)));
    }

    @Test
    public void testFilterAddedToServletContext(@Injectable final ServletContext mockServletContext) throws Exception {
        ravenServletContainerInitializer.onStartup(null, mockServletContext);

        new Verifications() {{
            mockServletContext.addListener(RavenServletRequestListener.class);
        }};
    }
}
