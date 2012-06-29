package net.kencochrane.raven;

import net.kencochrane.sentry.RavenUtils;
import org.apache.commons.lang.StringUtils;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;

/**
 * Transport class with default implementations for the only/popular Sentry transport methods.
 * <p>
 * As a user of this class you are responsible to select the correct transport layer depending on the Sentry DSN.
 * </p>
 * <p>
 * Usage example:
 * </p>
 * <pre><code>
 *     SentryDsn dsn = SentryDsn.build("http://public:secret@host/1");
 *     Transport transport = new Transport.Http(dsn);
 *     transport.send(message, System.currentTimeMillis());
 * </code></pre>
 */
public abstract class Transport {

    public interface Option {
        String INCLUDE_SIGNATURE = "raven.includeSignature";
    }

    public final SentryDsn dsn;
    public final boolean includeSignature;
    protected boolean started;

    public Transport(SentryDsn dsn) {
        this.dsn = dsn;
        this.includeSignature = dsn.getOptionAsBoolean(Option.INCLUDE_SIGNATURE, false);
    }

    public void start() {
        started = true;
    }

    public boolean isStarted() {
        return started;
    }

    public void stop() {
        started = false;
    }

    /**
     * Sends a message to Sentry.
     * <p>
     * Subclasses most likely will be more interested in {@link #doSend(String, String)}.
     * </p>
     *
     * @param messageBody message to send
     * @param timestamp   timestamp of the message
     * @throws IOException when something goes wrong when sending
     */
    public void send(String messageBody, long timestamp) throws IOException {
        if (includeSignature) {
            String hmacSignature = RavenUtils.getSignature(messageBody, timestamp, dsn.secretKey);
            String authHeader = buildAuthHeader(hmacSignature, timestamp, dsn.publicKey);
            doSend(messageBody, authHeader);
        } else {
            doSend(messageBody, buildAuthHeader(timestamp, dsn.publicKey));
        }
    }

    /**
     * Performs the actual sending of the message with the accompanying authentication header.
     *
     * @param messageBody message to send
     * @param authHeader  the authentication header, built with {@link #buildAuthHeader(String, long, String)}
     * @throws IOException when something goes wrong when sending
     */
    protected void doSend(String messageBody, String authHeader) throws IOException {
        throw new UnsupportedOperationException("Nothing to do here...");
    }

    public static String buildAuthHeader(long timestamp, String publicKey) {
        return buildAuthHeader(null, timestamp, publicKey);
    }

    @Deprecated
    public static String buildAuthHeader(String hmacSignature, long timestamp, String publicKey) {
        StringBuilder header = new StringBuilder();
        header.append("Sentry sentry_version=2.0");
        if (!StringUtils.isBlank(hmacSignature)) {
            header.append(",sentry_signature=").append(hmacSignature);
        }
        header.append(",sentry_timestamp=");
        header.append(timestamp);
        header.append(",sentry_key=");
        header.append(publicKey);
        header.append(",sentry_client=");
        header.append(RavenUtils.RAVEN_JAVA_VERSION);
        return header.toString();
    }

    /**
     * HTTP and HTTPS transport layer.
     */
    public static class Http extends Transport {

        public interface Option {
            String TIMEOUT = "raven.timeout";
            int TIMEOUT_DEFAULT = 10000;
        }

        public final URL url;
        public final int timeout;

        public Http(SentryDsn dsn) {
            super(dsn);
            try {
                this.url = new URL(dsn.toString(false) + "/api/store/");
            } catch (MalformedURLException e) {
                // We rely on the SentryDsn validating the URL so this really, *really* shouldn't happen
                throw new SentryDsn.InvalidDsnException("URL constructed from Sentry DSN is invalid", e);
            }
            this.timeout = dsn.getOptionAsInt(Option.TIMEOUT, Option.TIMEOUT_DEFAULT);
        }

        @Override
        protected void doSend(String messageBody, String authHeader) throws IOException {
            HttpURLConnection connection = getConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(timeout);
            connection.setRequestProperty("X-Sentry-Auth", authHeader);
            OutputStream output = connection.getOutputStream();
            output.write(messageBody.getBytes());
            output.close();
            connection.connect();
            InputStream input = connection.getInputStream();
            input.close();
        }

        protected HttpURLConnection getConnection() throws IOException {
            return (HttpURLConnection) url.openConnection();
        }

    }

    /**
     * A naive HTTPS transport layer, useful in case you set up your own Sentry instance with self-signed
     * certificates and don't want to add the certificate to your truststore (you really should though).
     */
    public static class NaiveHttps extends Http {

        private static final HostnameVerifier ACCEPT_ALL = new AcceptAllHostnameVerifier();
        public final HostnameVerifier hostnameVerifier;

        public NaiveHttps(SentryDsn dsn) {
            this(dsn, ACCEPT_ALL);
        }

        public NaiveHttps(SentryDsn dsn, HostnameVerifier hostnameVerifier) {
            super(dsn);
            this.hostnameVerifier = hostnameVerifier;
        }

        @Override
        protected HttpURLConnection getConnection() throws IOException {
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setHostnameVerifier(hostnameVerifier);
            return connection;
        }

    }

    /**
     * UDP transport layer.
     */
    public static class Udp extends Transport {

        private final DatagramSocket socket;

        public Udp(SentryDsn dsn) {
            super(dsn);
            try {
                socket = createSocket(dsn.host, dsn.port);
            } catch (SocketException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        protected void doSend(String messageBody, String authHeader) throws IOException {
            byte[] message = RavenUtils.toUtf8(authHeader + "\n\n" + messageBody);
            DatagramPacket packet = new DatagramPacket(message, message.length);
            socket.send(packet);
        }

        protected DatagramSocket createSocket(String host, int port) throws SocketException {
            DatagramSocket socket = new DatagramSocket();
            socket.connect(new InetSocketAddress(host, port));
            return socket;
        }

    }

    /**
     * A hostname verifier that actually just allows all hosts - used in combination with the {@link net.kencochrane.raven.Transport.NaiveHttps}
     * transport layer.
     */
    public static class AcceptAllHostnameVerifier implements HostnameVerifier {
        @Override
        public boolean verify(String hostname, SSLSession sslSession) {
            return true;
        }
    }

}
