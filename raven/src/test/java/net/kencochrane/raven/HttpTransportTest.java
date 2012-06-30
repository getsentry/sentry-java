package net.kencochrane.raven;

import mockit.Expectations;
import mockit.Mocked;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLPeerUnverifiedException;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.cert.Certificate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Test cases for {@link Transport.Http}
 */
public class HttpTransportTest {

    private CollectingHttpUrlConnection connection;
    private String messageBody = "MessageBodyDoesNotReallyMatter";
    private long timestamp = System.currentTimeMillis();

    @Test
    public void verifyRequest() throws IOException {
        SentryDsn dsn = SentryDsn.build("http://public:private@host:9999/1");
        Transport.Http transport = new Transport.Http(dsn);
        transport.send(messageBody, timestamp);

        // Verify
        assertEquals(Transport.Http.Option.TIMEOUT_DEFAULT, connection.connectTimeout);
        byte[] data = connection.output.toByteArray();
        assertEquals(messageBody, Utils.fromUtf8(data));
        assertEquals(Transport.buildAuthHeader(timestamp, "public"), connection.authHeader);
    }

    @Test
    public void timeoutOption() throws Exception {
        int timeout = 6000;
        String url = String.format("http://public:private@host:9999/1?%s=%d", Transport.Http.Option.TIMEOUT, timeout);
        Transport.Http transport = new Transport.Http(SentryDsn.build(url));
        transport.send(messageBody, timestamp);

        // Verify
        assertEquals(timeout, transport.timeout);
        assertEquals(timeout, connection.connectTimeout);
    }

    @Test
    public void withSignature_andTimeout() throws Exception {
        int timeout = 6000;
        String url = String.format("http://public:private@host:9999/1?%s=%d&%s=true", Transport.Http.Option.TIMEOUT, timeout, Transport.Option.INCLUDE_SIGNATURE);
        SentryDsn dsn = SentryDsn.build(url);
        Transport.Http transport = new Transport.Http(dsn);
        transport.send(messageBody, timestamp);

        // Verify
        assertTrue(dsn.getOptionAsBoolean(Transport.Option.INCLUDE_SIGNATURE, false));
        assertEquals(timeout, transport.timeout);
        assertEquals(timeout, connection.connectTimeout);
        String signature = Client.sign(messageBody, timestamp, "private");
        assertEquals(Transport.buildAuthHeader(signature, timestamp, "public"), connection.authHeader);
    }

    protected static CollectingHttpUrlConnection mockConnection() throws IOException {
        final CollectingHttpUrlConnection connection = new CollectingHttpUrlConnection();
        new Expectations() {
            @Mocked("getConnection")
            Transport.Http m;

            {
                m.getConnection();
                returns(connection);
            }
        };
        return connection;
    }

    @Before
    public void setUp() throws IOException {
        connection = mockConnection();
    }

    public static class CollectingHttpUrlConnection extends HttpsURLConnection {

        public static final URL LOCALHOST;

        public int connectTimeout;
        public String authHeader;
        public ByteArrayOutputStream output = new ByteArrayOutputStream();
        public HostnameVerifier hostnameVerifier;

        static {
            try {
                LOCALHOST = new URL("http://localhost");
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }

        protected CollectingHttpUrlConnection() {
            super(LOCALHOST);
        }

        @Override
        public void setConnectTimeout(int i) {
            this.connectTimeout = i;
        }

        @Override
        public void setRequestProperty(String k, String v) {
            Assert.assertEquals(k, "X-Sentry-Auth");
            this.authHeader = v;
        }

        @Override
        public OutputStream getOutputStream() throws IOException {
            return output;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public void connect() throws IOException {
            // Does nothing
        }

        @Override
        public void disconnect() {
            // Does nothing
        }

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public void setHostnameVerifier(HostnameVerifier hostnameVerifier) {
            this.hostnameVerifier = hostnameVerifier;
        }

        @Override
        public String getCipherSuite() {
            return null;
        }

        @Override
        public Certificate[] getLocalCertificates() {
            return new Certificate[0];
        }

        @Override
        public Certificate[] getServerCertificates() throws SSLPeerUnverifiedException {
            return new Certificate[0];
        }
    }

}
