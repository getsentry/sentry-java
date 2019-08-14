package io.sentry.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.sentry.config.provider.ConfigurationProvider;
import io.sentry.dsn.Dsn;
import org.junit.Test;

public class LookupTest {

    private final Dsn testDsn = new Dsn("sentry://user:pass@host/42?prop1=val1&prop2=val2");

    @Test
    public void testUsesHighPriorityConfigurationProvider() throws Exception {
        // given
        ConfigurationProvider highPrioProvider = mock(ConfigurationProvider.class);
        when(highPrioProvider.getProperty("prop1")).thenReturn("highPrio");

        Lookup lookup = new Lookup(highPrioProvider, mock(ConfigurationProvider.class));

        // when
        String prop1 = lookup.get("prop1", testDsn);
        String prop2 = lookup.get("prop2", testDsn);
        String prop3 = lookup.get("prop3", testDsn);

        // then
        assertEquals("highPrio", prop1);
        assertEquals("val2", prop2);
        assertNull(prop3);
    }

    @Test
    public void testusesDsnOptions() throws Exception {
        // given
        Lookup lookup = new Lookup(mock(ConfigurationProvider.class), mock(ConfigurationProvider.class));

        // when
        String prop1 = lookup.get("prop1", testDsn);
        String prop2 = lookup.get("prop2", testDsn);
        String prop3 = lookup.get("prop3", testDsn);

        // then
        assertEquals("val1", prop1);
        assertEquals("val2", prop2);
        assertNull(prop3);
    }

    @Test
    public void testUsesLowPriorityConfigurationProvider() throws Exception {
        // given
        ConfigurationProvider lowPrioProvider = mock(ConfigurationProvider.class);
        when(lowPrioProvider.getProperty("prop3")).thenReturn("lowPrio");

        Lookup lookup = new Lookup(mock(ConfigurationProvider.class), lowPrioProvider);

        // when
        String prop1 = lookup.get("prop1", testDsn);
        String prop2 = lookup.get("prop2", testDsn);
        String prop3 = lookup.get("prop3", testDsn);

        // then
        assertEquals("val1", prop1);
        assertEquals("val2", prop2);
        assertEquals("lowPrio", prop3);
    }
}
