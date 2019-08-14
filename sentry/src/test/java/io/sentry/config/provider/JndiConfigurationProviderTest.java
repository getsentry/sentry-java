package io.sentry.config.provider;

import static io.sentry.config.provider.JndiConfigurationProvider.DEFAULT_JNDI_PREFIX;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.naming.Context;

import io.sentry.config.provider.JndiConfigurationProvider.JndiContextProvider;
import org.junit.Test;

public class JndiConfigurationProviderTest {

    @Test
    public void testUsesJndiContextProvider() throws Exception {
        // given
        JndiContextProvider jndiProvider = mock(JndiContextProvider.class);
        Context jndiContext = mock(Context.class);
        when(jndiProvider.getContext()).thenReturn(jndiContext);
        when(jndiContext.lookup("java:comp/env/sentry/prop")).thenReturn("val");

        JndiConfigurationProvider provider = new JndiConfigurationProvider(DEFAULT_JNDI_PREFIX, jndiProvider);

        // when
        String val = provider.getProperty("prop");

        // then
        assertEquals("val", val);
    }

    @Test
    public void testUsesCustomJNDIRootPath() throws Exception {
        // given
        JndiContextProvider jndiProvider = mock(JndiContextProvider.class);
        Context jndiContext = mock(Context.class);
        when(jndiProvider.getContext()).thenReturn(jndiContext);
        when(jndiContext.lookup("totally-random-jndi-prefix/prop")).thenReturn("val");

        JndiConfigurationProvider provider = new JndiConfigurationProvider("totally-random-jndi-prefix/", jndiProvider);

        // when
        String val = provider.getProperty("prop");

        // then
        assertEquals("val", val);
    }
}
