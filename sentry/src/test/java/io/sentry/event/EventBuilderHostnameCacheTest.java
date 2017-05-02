package io.sentry.event;

import io.sentry.BaseTest;
import mockit.*;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.net.InetAddress;
import java.util.concurrent.TimeUnit;

import static mockit.Deencapsulation.getField;
import static mockit.Deencapsulation.setField;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@Test(singleThreaded = true)
public class EventBuilderHostnameCacheTest extends BaseTest {
    @Injectable
    private InetAddress mockLocalHost = null;
    @Injectable("serverName")
    private String mockLocalHostName = null;
    @Injectable
    private InetAddress mockTimingOutLocalHost = null;

    private static void resetHostnameCache() {
        setField(getHostnameCache(), "expirationTimestamp", 0L);
        setField(getHostnameCache(), "hostname", EventBuilder.DEFAULT_HOSTNAME);
    }

    private static Object getHostnameCache() {
        return getField(EventBuilder.class, "HOSTNAME_CACHE");
    }

    @BeforeMethod
    public void setUp() throws Exception {
        new NonStrictExpectations() {{
            mockLocalHost.getCanonicalHostName();
            result = mockLocalHostName;

            mockTimingOutLocalHost.getCanonicalHostName();
            result = new RuntimeException("For all intents and purposes, an exception is the same as a timeout");
        }};
        // Clean Hostname Cache
        resetHostnameCache();
    }

    @Test
    public void successfulHostnameRetrievalIsCachedForFiveHours(
            @SuppressWarnings("unused") @Mocked("currentTimeMillis") final System system)
            throws Exception {
        new NonStrictExpectations(InetAddress.class) {{
            System.currentTimeMillis();
            result = 1L;
            InetAddress.getLocalHost();
            result = mockLocalHost;
        }};

        new EventBuilder().build();
        final long expirationTime = Deencapsulation.<Long>getField(getHostnameCache(), "expirationTimestamp");

        assertThat(expirationTime, is(TimeUnit.HOURS.toMillis(5) + System.currentTimeMillis()));
    }

    @Test
    public void unsuccessfulHostnameRetrievalIsCachedForOneSecond(
            @SuppressWarnings("unused") @Mocked("currentTimeMillis") final System system)
            throws Exception {
        new NonStrictExpectations(InetAddress.class) {{
            System.currentTimeMillis();
            result = 1L;
            InetAddress.getLocalHost();
            result = mockTimingOutLocalHost;
        }};

        new EventBuilder().build();
        final long expirationTime = Deencapsulation.<Long>getField(getHostnameCache(), "expirationTimestamp");

        assertThat(expirationTime, is(TimeUnit.SECONDS.toMillis(1) + System.currentTimeMillis()));
    }

    @Test
    public void unsuccessfulHostnameRetrievalUsesLastKnownCachedValue() throws Exception {
        new NonStrictExpectations(InetAddress.class) {{
            InetAddress.getLocalHost();
            result = mockLocalHost;
            result = mockTimingOutLocalHost;
        }};

        new EventBuilder().build();
        setField(getHostnameCache(), "expirationTimestamp", 0l);
        Event event = new EventBuilder().build();

        assertThat(event.getServerName(), is(mockLocalHostName));
        new Verifications() {{
            mockLocalHost.getCanonicalHostName();
            mockTimingOutLocalHost.getCanonicalHostName();
        }};
    }
}
