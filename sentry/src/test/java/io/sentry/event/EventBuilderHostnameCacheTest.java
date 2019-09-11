package io.sentry.event;

import io.sentry.BaseTest;
import io.sentry.time.FixedClock;
import org.junit.Before;
import org.junit.Test;

import java.net.InetAddress;
import java.util.Date;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class EventBuilderHostnameCacheTest extends BaseTest {
    private InetAddress mockLocalHost = null;
    private InetAddress mockTimingOutLocalHost = null;
    private FixedClock fixedClock = new FixedClock(new Date());

    @Before
    public void setUp() throws Exception {
        mockLocalHost = mock(InetAddress.class);
        mockTimingOutLocalHost = mock(InetAddress.class);
        when(mockLocalHost.getCanonicalHostName()).thenReturn("mockLocalhost");
        when(mockTimingOutLocalHost.getCanonicalHostName())
                .thenThrow(new RuntimeException("For all intents and purposes, an exception is the same as a timeout"));
    }

    private EventBuilder.HostnameCache createCacheUsingLocalhost(final InetAddress mockAddress) {
        return new EventBuilder.HostnameCache(EventBuilder.HOSTNAME_CACHE_DURATION, fixedClock, new Callable<InetAddress>() {
            @Override
            public InetAddress call() throws Exception {
                return mockAddress;
            }
        });
    }

    @Test
    public void successfulHostnameRetrievalIsCachedForFiveHours() throws Exception {
        EventBuilder.HostnameCache cache = createCacheUsingLocalhost(mockLocalHost);
        cache.getHostname();
        assertThat(cache.expirationTimestamp, is(EventBuilder.HOSTNAME_CACHE_DURATION + fixedClock.millis()));
    }

    @Test
    public void unsuccessfulHostnameRetrievalIsCachedForOneSecond() throws Exception {
        EventBuilder.HostnameCache cache = createCacheUsingLocalhost(mockTimingOutLocalHost);
        cache.getHostname();
        assertThat(cache.expirationTimestamp, is(TimeUnit.SECONDS.toMillis(1) + fixedClock.millis()));
    }

    @Test
    public void unsuccessfulHostnameRetrievalUsesDefaultHostnameAsResult() throws Exception {
        EventBuilder.HostnameCache cache = createCacheUsingLocalhost(mockTimingOutLocalHost);
        assertThat(cache.getHostname(), is(EventBuilder.DEFAULT_HOSTNAME));
    }

    @Test
    public void unsuccessfulHostnameRetrievalUsesLastKnownCachedValue() throws Exception {
        InetAddress localhost = mock(InetAddress.class);
        when(localhost.getCanonicalHostName()).thenReturn("mockLocalhost").thenThrow(new RuntimeException());

        EventBuilder.HostnameCache cache = createCacheUsingLocalhost(localhost);

        // get the "last known cached value"
        String resolved = cache.getHostname();
        assertThat(resolved, is("mockLocalhost"));

        // expire the cache
        fixedClock.tick(cache.cacheDuration + 1, TimeUnit.MILLISECONDS);

        // check that the second failing invocation returns the previously cached value
        resolved = cache.getHostname();
        assertThat(resolved, is("mockLocalhost"));
    }
}
