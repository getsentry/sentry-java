package net.kencochrane.raven.connection;

import com.google.common.base.Charsets;
import net.kencochrane.raven.Raven;
import net.kencochrane.raven.event.Event;
import net.kencochrane.raven.marshaller.Marshaller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.TimeUnit;

/**
 * Basic connection to a Sentry server, using HTTP and HTTPS.
 * <p>
 * It is possible to enable the "naive mode" to allow a connection over SSL using a certificate with a wildcard.
 * </p>
 */
public class HttpConnection extends AbstractConnection {
    private static final Logger logger = LoggerFactory.getLogger(HttpConnection.class);
    /**
     * HTTP Header for the user agent.
     */
    private static final String USER_AGENT = "User-Agent";
    /**
     * HTTP Header for the authentication to Sentry.
     */
    private static final String SENTRY_AUTH = "X-Sentry-Auth";
    /**
     * Default timeout of an HTTP connection to Sentry.
     */
    private static final int DEFAULT_TIMEOUT = (int) TimeUnit.SECONDS.toMillis(1);
    /**
     * HostnameVerifier allowing wildcard certificates to work without adding them to the truststore.
     */
    private static final HostnameVerifier NAIVE_VERIFIER = new HostnameVerifier() {
        @Override
        public boolean verify(String hostname, SSLSession sslSession) {
            return true;
        }
    };
    /**
     * URL of the Sentry endpoint.
     */
    private final URL sentryUrl;
    /**
     * Marshaller used to transform and send the {@link Event} over a stream.
     */
    private Marshaller marshaller;
    /**
     * Timeout of an HTTP connection to Sentry.
     */
    private int timeout = DEFAULT_TIMEOUT;
    /**
     * Setting allowing to bypass the security system which requires wildcard certificates
     * to be added to the truststore.
     */
    private boolean bypassSecurity = false;

    /**
     * Creates an HTTP connection to a Sentry server.
     *
     * @param sentryUrl URL to the Sentry API.
     * @param publicKey public key of the current project.
     * @param secretKey private key of the current project.
     */
    public HttpConnection(URL sentryUrl, String publicKey, String secretKey) {
        super(publicKey, secretKey);
        this.sentryUrl = sentryUrl;
    }

    /**
     * Automatically determines the URL to the HTTP API of Sentry.
     *
     * @param sentryUri URI of the Sentry instance.
     * @param projectId unique identifier of the current project.
     * @return an URL to the HTTP API of Sentry.
     */
    public static URL getSentryApiUrl(URI sentryUri, String projectId) {
        try {
            String url = sentryUri.toString() + "api/" + projectId + "/store/";
            return new URL(url);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Couldn't build a valid URL from the Sentry API.", e);
        }
    }

    /**
     * Opens a connection to the Sentry API allowing to send new events.
     *
     * @return an HTTP connection to Sentry.
     */
    protected HttpURLConnection getConnection() {
        try {
            HttpURLConnection connection = (HttpURLConnection) sentryUrl.openConnection();
            if (bypassSecurity && connection instanceof HttpsURLConnection) {
                ((HttpsURLConnection) connection).setHostnameVerifier(NAIVE_VERIFIER);
            }
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(timeout);
            connection.setRequestProperty(USER_AGENT, Raven.NAME);
            connection.setRequestProperty(SENTRY_AUTH, getAuthHeader());
            return connection;
        } catch (IOException e) {
            throw new IllegalStateException("Couldn't set up a connection to the sentry server.", e);
        }
    }

    @Override
    protected void doSend(Event event) {
        HttpURLConnection connection = getConnection();
        try {
            connection.connect();
            OutputStream outputStream = connection.getOutputStream();
            marshaller.marshall(event, outputStream);
            outputStream.close();
            connection.getInputStream().close();
        } catch (IOException e) {
            String errorMessage;
            if (connection.getErrorStream() != null)
                errorMessage = getErrorMessageFromStream(connection.getErrorStream());
            else
                errorMessage = "An exception occurred while submitting the event to the sentry server.";
            throw new ConnectionException(errorMessage, e);
        } finally {
            connection.disconnect();
        }
    }

    private String getErrorMessageFromStream(InputStream errorStream) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(errorStream, Charsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        try {
            String line;
            while ((line = reader.readLine()) != null)
                sb.append(line).append("\n");
            //Remove last \n
            sb.deleteCharAt(sb.length() - 1);

        } catch (Exception e2) {
            logger.error("Exception while reading the error message from the connection.", e2);
        }
        return sb.toString();
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public void setMarshaller(Marshaller marshaller) {
        this.marshaller = marshaller;
    }

    public void setBypassSecurity(boolean bypassSecurity) {
        this.bypassSecurity = bypassSecurity;
    }

    @Override
    public void close() throws IOException {
    }
}
