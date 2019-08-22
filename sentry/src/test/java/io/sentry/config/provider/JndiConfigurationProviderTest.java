package io.sentry.config.provider;

import static io.sentry.config.provider.JndiConfigurationProvider.DEFAULT_JNDI_PREFIX;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.NoInitialContextException;

import io.sentry.config.provider.JndiConfigurationProvider.JndiContextProvider;
import org.junit.Test;
import org.mockito.Mockito;

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

    @Test
    public void testReturnsNullOnNamingException() throws Exception {
        testReturnsNullOnException(new NamingException());
    }

    @Test
    public void testReturnsNullOnNoInitialContextException() throws Exception {
        testReturnsNullOnException(new NoInitialContextException());
    }

    @Test
    public void testReturnsNullOnRuntimeException() throws Exception {
        testReturnsNullOnException(new IllegalStateException());
    }

    private void testReturnsNullOnException(Exception e) throws Exception {
        // given
        JndiContextProvider jndiProvider = mock(JndiContextProvider.class);
        Context jndiContext = mock(Context.class);
        when(jndiProvider.getContext()).thenReturn(jndiContext);
        when(jndiContext.lookup(anyString())).thenThrow(e);

        JndiConfigurationProvider provider = new JndiConfigurationProvider("something", jndiProvider);

        // when
        String val = provider.getProperty("property");

        // then
        assertNull(val);
    }
}
