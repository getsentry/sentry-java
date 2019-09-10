package io.sentry.servlet;

import io.sentry.BaseTest;
import org.junit.Test;

import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import java.util.ServiceLoader;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class SentryServletContainerInitializerTest extends BaseTest {

    @Test
    public void testInitializerInjectedViaServiceLoader() throws Exception {
        ServiceLoader<ServletContainerInitializer> serviceLoader = ServiceLoader.load(ServletContainerInitializer.class);
        assertThat(serviceLoader, contains(instanceOf(SentryServletContainerInitializer.class)));
    }

    @Test
    public void testFilterAddedToServletContext() throws Exception {
        ServletContext mockServletContext = mock(ServletContext.class);
        new SentryServletContainerInitializer().onStartup(null, mockServletContext);

        verify(mockServletContext).addListener(eq(SentryServletRequestListener.class));
    }
}
