package io.sentry.connection;

import io.sentry.environment.SentryEnvironment;
import io.sentry.event.Event;
import io.sentry.marshaller.Marshaller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;

/**
 * Basic connection to a Sentry server, using HTTP and HTTPS.
 * <p>
 * It is possible to enable the "naive mode" to allow a connection over SSL using a certificate with a wildcard.
 */
public class HttpConnection extends AbstractConnection {
    /**
     * HTTP code `429 Too Many Requests`, which is not included in HttpURLConnection.
     */
    public static final int HTTP_TOO_MANY_REQUESTS = 429;
    private static final Charset UTF_8 = Charset.forName("UTF-8");
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
     * Default connection timeout of an HTTP connection to Sentry.
     */
    private static final int DEFAULT_CONNECTION_TIMEOUT = (int) TimeUnit.SECONDS.toMillis(1);
    /**
     * Default read timeout of an HTTP connection to Sentry.
     */
    private static final int DEFAULT_READ_TIMEOUT = (int) TimeUnit.SECONDS.toMillis(5);

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
     * Optional instance of an HTTP proxy server to use.
     */
    private final Proxy proxy;
    /**
     * Optional instance of an EventSampler to use.
     */
    private EventSampler eventSampler;
    /**
     * Marshaller used to transform and send the {@link Event} over a stream.
     */
    private Marshaller marshaller;
    /**
     * Timeout to connect of an HTTP connection to Sentry.
     */
    private int connectionTimeout = DEFAULT_CONNECTION_TIMEOUT;
    /**
     * Read timeout of an HTTP connection to Sentry.
     */
    private int readTimeout = DEFAULT_READ_TIMEOUT;

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
     * @param proxy address of HTTP proxy or null if using direct connections.
     * @param eventSampler EventSampler instance to use, or null to not sample events.
     */
    public HttpConnection(URL sentryUrl, String publicKey, String secretKey, Proxy proxy, EventSampler eventSampler) {
        super(publicKey, secretKey);
        this.sentryUrl = sentryUrl;
        this.proxy = proxy;
        this.eventSampler = eventSampler;
    }

    /**
     * Automatically determines the URL to the HTTP API of Sentry.
     *
     * @param sentryUri URI of the Sentry server.
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
            HttpURLConnection connection;
            if (proxy != null) {
                connection = (HttpURLConnection) sentryUrl.openConnection(proxy);
            } else {
                connection = (HttpURLConnection) sentryUrl.openConnection();
            }

            if (bypassSecurity && connection instanceof HttpsURLConnection) {
                ((HttpsURLConnection) connection).setHostnameVerifier(NAIVE_VERIFIER);
            }
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(connectionTimeout);
            connection.setReadTimeout(readTimeout);
            connection.setRequestProperty(USER_AGENT, SentryEnvironment.getSentryName());
            connection.setRequestProperty(SENTRY_AUTH, getAuthHeader());

            if (marshaller.getContentType() != null) {
                connection.setRequestProperty("Content-Type", marshaller.getContentType());
            }

            if (marshaller.getContentEncoding() != null) {
                connection.setRequestProperty("Content-Encoding", marshaller.getContentEncoding());
            }

            return connection;
        } catch (IOException e) {
            throw new IllegalStateException("Couldn't set up a connection to the Sentry server.", e);
        }
    }

    @Override
    protected void doSend(Event event) throws ConnectionException {
        if (eventSampler != null && !eventSampler.shouldSendEvent(event)) {
            return;
        }

        HttpURLConnection connection = getConnection();
        try {
            connection.connect();
            OutputStream outputStream = connection.getOutputStream();
            marshaller.marshall(event, outputStream);
            outputStream.close();
            connection.getInputStream().close();
        } catch (IOException e) {
            Long retryAfterMs = null;
            String retryAfterHeader = connection.getHeaderField("Retry-After");
            if (retryAfterHeader != null) {
                // CHECKSTYLE.OFF: EmptyCatchBlock
                try {
                    // CHECKSTYLE.OFF: MagicNumber
                    retryAfterMs = (long) (Double.parseDouble(retryAfterHeader) * 1000L); // seconds -> milliseconds
                    // CHECKSTYLE.ON: MagicNumber
                } catch (NumberFormatException nfe) {
                    // noop, use default retry
                }
                // CHECKSTYLE.ON: EmptyCatchBlock
            }

            Integer responseCode = null;
            try {
                responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_FORBIDDEN) {
                    logger.debug("Event '" + event.getId() + "' was rejected by the Sentry server due to a filter.");
                    return;
                } else if (responseCode == HTTP_TOO_MANY_REQUESTS) {
                    /*
                    If the response is a 429 we rethrow as a TooManyRequestsException so that we can
                    avoid logging this is an error.
                    */
                    throw new TooManyRequestsException(
                            "Too many requests to Sentry: https://docs.sentry.io/learn/quotas/",
                            e, retryAfterMs, responseCode);
                }
            } catch (IOException responseCodeException) {
                // pass
            }

            String errorMessage = null;
            final InputStream errorStream = connection.getErrorStream();
            if (errorStream != null) {
                errorMessage = getErrorMessageFromStream(errorStream);
            }
            if (null == errorMessage || errorMessage.isEmpty()) {
                errorMessage = "An exception occurred while submitting the event to the Sentry server.";
            }

            throw new ConnectionException(errorMessage, e, retryAfterMs, responseCode);
        } finally {
            connection.disconnect();
        }
    }

    private String getErrorMessageFromStream(InputStream errorStream) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(errorStream, UTF_8));
        StringBuilder sb = new StringBuilder();
        try {
            String line;
            // ensure we do not add "\n" to the last line
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (!first) {
                    sb.append("\n");
                }
                sb.append(line);
                first = false;
            }
        } catch (Exception e2) {
            logger.error("Exception while reading the error message from the connection.", e2);
        }
        return sb.toString();
    }

    /**
     * This will set the timeout that is used in establishing a connection to the url.
     * By default this is set to 1 second.
     *
     * @deprecated Use setConnectionTimeout instead.
     * @param timeout New timeout to set. If 0 is used (java default) wait forever.
     */
    @Deprecated
    public void setTimeout(int timeout) {
        this.connectionTimeout = timeout;
    }

    /**
     * This will set the timeout that is used in establishing a connection to the url.
     * By default this is set to 5 second.
     *
     * @param timeout New timeout to set. If 0 is used (java default) wait forever.
     */
    public void setConnectionTimeout(int timeout) {
        this.connectionTimeout = timeout;
    }

    /**
     * This will set the timeout that is used in reading data on an already established connection.
     * By default this is set to 1 seconds.
     *
     * @param timeout New timeout to set. If 0 is used (java default) wait forever.
     */
    public void setReadTimeout(int timeout) {
        this.readTimeout = timeout;
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
