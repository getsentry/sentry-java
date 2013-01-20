package net.kencochrane.raven;

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
 * As a user of this class you are responsible for selecting the correct transport layer depending on the Sentry DSN.
 * The {@link Client} will select the correct transport layer based on its registry of transport layers.
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

    /**
     * General transport options.
     */
    public interface Option {

        /**
         * Sending the HMAC signature along with the message has been deprecated but you can still enable through the
         * {@link SentryDsn#options}. Set this option to <code>true</code> to send the signature.
         */
        String INCLUDE_SIGNATURE = "raven.includeSignature";
    }

    /**
     * The DSN used by this transport.
     */
    public final SentryDsn dsn;

    /**
     * Whether the signature should be included or not.
     */
    public final boolean includeSignature;

    /**
     * Whether the transport has started.
     * <p>
     * This is mostly provided because the {@link AsyncTransport} wrapper should allow users of this class to control
     * the lifecycle through the {@link #start()} and {@link #stop()} methods.
     * </p>
     */
    protected boolean started;

    /**
     * The required transport constructor.
     * <p>
     * Each transport class registered with the {@link Client} must provide a similar constructor.
     * </p>
     *
     * @param dsn the Sentry DSN
     */
    public Transport(SentryDsn dsn) {
        this.dsn = dsn;
        this.includeSignature = dsn.getOptionAsBoolean(Option.INCLUDE_SIGNATURE, false);
    }

    /**
     * Starts the transport layer.
     */
    public void start() {
        started = true;
    }

    /**
     * Indicates whether the transport layer has been started.
     *
     * @return <code>true</code> when started
     */
    public boolean isStarted() {
        return started;
    }

    /**
     * Stops the transport layer.
     */
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
            String hmacSignature = Client.sign(messageBody, timestamp, dsn.secretKey);
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

    /**
     * Constructs the <code>X-Sentry-Auth</code> header.
     *
     * @param timestamp timestamp
     * @param publicKey public key
     * @return the value for the <code>X-Sentry-Auth</code> header.
     */
    public static String buildAuthHeader(long timestamp, String publicKey) {
        return buildAuthHeader(null, timestamp, publicKey);
    }

    /**
     * Constructs the <code>X-Sentry-Auth</code> header.
     *
     * @param hmacSignature the HMAC signature of the message
     * @param timestamp     timestamp
     * @param publicKey     public key
     * @return the value for the <code>X-Sentry-Auth</code> header.
     * @deprecated Usage of the signature has been deprecated in Sentry versions 4.6 and higher.
     */
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
        header.append(Utils.Client.NAME);
        return header.toString();
    }

    /**
     * HTTP and HTTPS transport layer.
     */
    public static class Http extends Transport {

        /**
         * Options for this transport.
         */
        public interface Option {

            /**
             * The connect timeout option key.
             */
            String TIMEOUT = "raven.timeout";

            /**
             * The default timeout applied to connections.
             */
            int TIMEOUT_DEFAULT = 10000;
        }

        /**
         * The URL to post to.
         */
        public final URL url;

        /**
         * The timeout applied to connections originating from this instance.
         */
        public final int timeout;

        /**
         * Constructor.
         *
         * @param dsn the Sentry dsn
         */
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
     * A naive HTTPS transport layer, useful in case you're using wildcard SSL certificates which
     * Java doesn't handle that well.
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
            byte[] message = Utils.toUtf8(authHeader + "\n\n" + messageBody);
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
