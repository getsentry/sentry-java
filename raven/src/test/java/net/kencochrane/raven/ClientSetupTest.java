package net.kencochrane.raven;

import org.apache.commons.codec.binary.Base64;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;

import static net.kencochrane.raven.Utils.decompress;
import static net.kencochrane.raven.Utils.fromUtf8;
import static org.junit.Assert.*;

/**
 * Test cases for {@link Client}.
 */
public class ClientSetupTest {

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

    @Test
    public void constructor_noDsn() {
        Client client = new Client();
        assertTrue(client.isDisabled());
        client.start();
        assertFalse(client.isStarted());
        assertEquals("-1", client.captureMessage("Hi"));
    }

    @Test
    public void messageCompression() throws ParseException {
        Client.register("http", DummyTransport.class);
        Client client = new Client(SentryDsn.build("http://public:private@localhost:9000/1"));
        client.captureMessage("hello");
        byte[] bytes = Base64.decodeBase64(DummyTransport.messageBody.getBytes());
        // This should not throw an exception
        new JSONParser().parse(fromUtf8(decompress(bytes)));

        client.setMessageCompressionEnabled(false);
        client.captureMessage("hello");
        bytes = Base64.decodeBase64(DummyTransport.messageBody.getBytes());
        // Neither should this
        new JSONParser().parse(fromUtf8(bytes));
    }

    @Test
    public void newTransport() {
        // HTTP
        String dsn = "http://public:private@localhost/1";
        Transport transport = Client.newTransport(SentryDsn.build(dsn));
        assertNotNull(transport);
        assertTrue(transport instanceof Transport.Http);

        // HTTP with custom transport
        Client.register("http", DummyTransport.class);
        transport = Client.newTransport(SentryDsn.build(dsn));
        assertNotNull(transport);
        assertTrue(transport instanceof DummyTransport);

        // HTTPS
        dsn = "https://public:private@localhost/1";
        transport = Client.newTransport(SentryDsn.build(dsn));
        assertNotNull(transport);
        assertTrue(transport instanceof Transport.Http);

        // HTTPS with custom transport
        Client.register("https", DummyTransport.class);
        transport = Client.newTransport(SentryDsn.build(dsn));
        assertNotNull(transport);
        assertTrue(transport instanceof DummyTransport);

        // Naive HTTPS
        dsn = "naive+https://public:private@localhost/1";
        transport = Client.newTransport(SentryDsn.build(dsn));
        assertNotNull(transport);
        assertTrue(transport instanceof Transport.NaiveHttps);

        // Naive HTTPS with custom transport
        Client.register("naive+https", DummyTransport.class);
        transport = Client.newTransport(SentryDsn.build(dsn));
        assertNotNull(transport);
        assertTrue(transport instanceof DummyTransport);

        // UDP
        dsn = "udp://public:private@localhost:9000/1";
        transport = Client.newTransport(SentryDsn.build(dsn));
        assertNotNull(transport);
        assertTrue(transport instanceof Transport.Udp);

        // UDP with custom transport
        Client.register("udp", DummyTransport.class);
        transport = Client.newTransport(SentryDsn.build(dsn));
        assertNotNull(transport);
        assertTrue(transport instanceof DummyTransport);

        // Custom
        dsn = "custom://public:private@localhost:9000/1";
        try {
            Client.newTransport(SentryDsn.build(dsn));
            fail("Expected an exception");
        } catch (Client.InvalidConfig e) {
            // Ok
        }

        Client.register("custom", DummyTransport.class);
        transport = Client.newTransport(SentryDsn.build(dsn));
        assertNotNull(transport);
        assertTrue(transport instanceof DummyTransport);

        // Async
        Client.register("udp", Transport.Udp.class);
        dsn = "async+udp://public:private@localhost:9000/1";
        transport = Client.newTransport(SentryDsn.build(dsn));
        assertNotNull(transport);
        assertTrue(transport instanceof AsyncTransport);
        assertTrue(((AsyncTransport) transport).transport instanceof Transport.Udp);

        // Async with custom transport
        Client.register("async", DummyTransport.class);
        try {
            Client.newTransport(SentryDsn.build(dsn));
            fail("Expected an exception because " + DummyTransport.class + " does not have the right signature");
        } catch (Client.InvalidConfig e) {
            // Ok
        }

        // Async with valid custom transport
        Client.register("async", DummyAsyncTransport.class);
        transport = Client.newTransport(SentryDsn.build(dsn));
        assertNotNull(transport);
        assertTrue(transport instanceof DummyAsyncTransport);
        assertTrue(((DummyAsyncTransport) transport).transport instanceof Transport.Udp);
    }

    @After
    public void tearDown() {
        System.setProperty(Utils.SENTRY_DSN, "");
        Client.registerDefaults();
    }

    protected void verifyClient(Client client, Class<? extends Transport> transportClass, boolean async) {
        verifyClient(client, transportClass, async, true);
    }

    protected void verifyClient(Client client, Class<? extends Transport> transportClass, boolean async, boolean autostart) {
        assertTrue(!autostart || client.isStarted());
        assertFalse(client.isDisabled());
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

    protected static class DummyTransport extends Transport {

        public static String messageBody;
        public static long timestamp;

        public DummyTransport(SentryDsn dsn) {
            super(dsn);
        }

        @Override
        public void send(String messageBody, long timestamp) throws IOException {
            DummyTransport.messageBody = messageBody;
            DummyTransport.timestamp = timestamp;
        }
    }

    protected static class DummyAsyncTransport extends Transport {

        public final Transport transport;

        public DummyAsyncTransport(Transport transport) {
            super(transport.dsn);
            this.transport = transport;
        }

        public static Transport build(Transport transport) {
            return new DummyAsyncTransport(transport);
        }

    }

}
