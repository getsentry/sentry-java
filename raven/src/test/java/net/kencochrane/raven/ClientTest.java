package net.kencochrane.raven;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * Test cases for {@link Client}.
 */
public class ClientTest {

    @Test
    public void defaultConstructor() {
        // Plain HTTP
        System.setProperty(Utils.SENTRY_DSN, "http://public:private@localhost:9000/1");
        verifyClient(new Client(), Transport.Http.class, false);
        // Async HTTP
        System.setProperty(Utils.SENTRY_DSN, "async+http://public:private@localhost:9000/1");
        verifyClient(new Client(), Transport.Http.class, true);
        // Plain HTTPS
        System.setProperty(Utils.SENTRY_DSN, "https://public:private@localhost:9000/1");
        verifyClient(new Client(), Transport.Http.class, false);
        // Async HTTPS
        System.setProperty(Utils.SENTRY_DSN, "async+https://public:private@localhost:9000/1");
        verifyClient(new Client(), Transport.Http.class, true);
        // Naive HTTPS
        System.setProperty(Utils.SENTRY_DSN, "naive+https://public:private@localhost:9000/1");
        verifyClient(new Client(), Transport.NaiveHttps.class, false);
        // Async Naive HTTPS
        System.setProperty(Utils.SENTRY_DSN, "async+naive+https://public:private@localhost:9000/1");
        verifyClient(new Client(), Transport.NaiveHttps.class, true);
        // Plain UDP
        System.setProperty(Utils.SENTRY_DSN, "udp://public:private@localhost:9000/1");
        verifyClient(new Client(), Transport.Udp.class, false);
        // Async UDP
        System.setProperty(Utils.SENTRY_DSN, "async+udp://public:private@localhost:9000/1");
        verifyClient(new Client(), Transport.Udp.class, true);
    }

    @Test
    public void constructor_withOptionToAutoStart() {
        // Plain HTTP
        System.setProperty(Utils.SENTRY_DSN, "http://public:private@localhost:9000/1");
        verifyClient(new Client(true), Transport.Http.class, false, true);
        verifyClient(new Client(false), Transport.Http.class, false, false);
    }

    @Test
    public void constructor_specifyDsn() {
        final String url = "://public:private@localhost:9000/1";
        // Ignore this system property
        System.setProperty(Utils.SENTRY_DSN, "udp" + url);
        SentryDsn dsn = SentryDsn.build("http" + url, null, null);
        // Make sure the supplied dsn was used
        verifyClient(new Client(dsn), Transport.Http.class, false, true);
        verifyClient(new Client(dsn, true), Transport.Http.class, false, true);
        verifyClient(new Client(dsn, false), Transport.Http.class, false, false);
    }

    @After
    public void tearDown() {
        System.setProperty(Utils.SENTRY_DSN, "");
    }

    protected void verifyClient(Client client, Class<? extends Transport> transportClass, boolean async) {
        verifyClient(client, transportClass, async, true);
    }

    protected void verifyClient(Client client, Class<? extends Transport> transportClass, boolean async, boolean autostart) {
        assertTrue(!autostart || client.isStarted());
        if (async) {
            assertTrue(client.transport instanceof AsyncTransport);
            assertTrue(((AsyncTransport) client.transport).transport.getClass().isAssignableFrom(transportClass));
        } else {
            if (!client.isStarted()) {
                client.start();
            }
            assertTrue(client.transport.getClass().isAssignableFrom(transportClass));
        }
    }

}
