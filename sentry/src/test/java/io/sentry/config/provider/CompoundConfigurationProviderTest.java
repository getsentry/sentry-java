package io.sentry.config.provider;

import static java.util.Arrays.asList;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Test;


public class CompoundConfigurationProviderTest {

    @Test
    public void testReturnsFirstNonNullValue() {
        // given
        ConfigurationProvider first = mock(ConfigurationProvider.class);
        ConfigurationProvider second = mock(ConfigurationProvider.class);
        ConfigurationProvider third = mock(ConfigurationProvider.class);

        when(second.getProperty(anyString())).thenReturn("non-null");

        CompoundConfigurationProvider compoundLocator = new CompoundConfigurationProvider(asList(first, second, third));

        // when
        String val = compoundLocator.getProperty("prop");

        // then
        assertEquals("non-null", val);

        verify(first, times(1)).getProperty(eq("prop"));
        verify(second, times(1)).getProperty(eq("prop"));
        verify(third, never()).getProperty(anyString());
    }
}
