package net.kencochrane.raven.connection;

import net.kencochrane.raven.Utils;
import net.kencochrane.raven.connection.marshaller.Marshaller;
import net.kencochrane.raven.connection.marshaller.SimpleJsonMarshaller;
import net.kencochrane.raven.event.LoggedEvent;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Basic connection to a Sentry server, using HTTP and HTTPS.
 */
public class HttpConnection extends AbstractConnection {
    public static final String USER_AGENT = "User-Agent";
    private static final Logger logger = Logger.getLogger(HttpConnection.class.getCanonicalName());
    private static final String SENTRY_AUTH = "X-Sentry-Auth";
    private static final int DEFAULT_TIMEOUT = 10000;
    private static final HostnameVerifier NAIVE_VERIFIER = new HostnameVerifier() {
        @Override
        public boolean verify(String hostname, SSLSession sslSession) {
            return true;
        }
    };
    private Marshaller marshaller = new SimpleJsonMarshaller();
    private int timeout = DEFAULT_TIMEOUT;
    private boolean bypassSecurity;

    public HttpConnection(Dsn dsn) {
        super(dsn);

        // Check if a timeout is set
        if (dsn.getOptions().containsKey(Dsn.TIMEOUT_OPTION))
            setTimeout(Integer.parseInt(dsn.getOptions().get(Dsn.TIMEOUT_OPTION)));

        // Check if the naive mode is on
        if (dsn.getProtocolSettings().contains(Dsn.NAIVE_PROTOCOL))
            setBypassSecurity(true);
    }

    private URL getSentryUrl() {
        try {
            String url = getDsn().getUri().toString() + "api/" + getDsn().getProjectId() + "/store/";
            //TODO: Cache the URL?
            return new URL(url);
        } catch (Exception e) {
            // TODO: Runtime exception... Really???
            throw new RuntimeException("Couldn't get a valid URL from the DSN", e);
        }
    }

    private HttpURLConnection getConnection() {
        try {
            HttpURLConnection connection = (HttpURLConnection) getSentryUrl().openConnection();
            if (bypassSecurity && connection instanceof HttpsURLConnection) {
                ((HttpsURLConnection) connection).setHostnameVerifier(NAIVE_VERIFIER);
            }
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(timeout);
            connection.setRequestProperty(USER_AGENT, Utils.Client.NAME);
            connection.setRequestProperty(SENTRY_AUTH, getAuthHeader());
            return connection;
        } catch (IOException e) {
            throw new IllegalStateException("Couldn't set up a connection to the sentry server.", e);
        }
    }

    @Override
    public void send(LoggedEvent event) {
        HttpURLConnection connection = getConnection();
        try {
            connection.connect();
            marshaller.marshall(event, connection.getOutputStream());
            connection.getOutputStream().close();
            connection.getInputStream().close();
        } catch (IOException e) {
            if (connection.getErrorStream() != null) {
                logger.log(Level.SEVERE, getErrorMessageFromStream(connection.getErrorStream()), e);
            } else {
                logger.log(Level.SEVERE,
                        "An exception occurred while submitting the event to the sentry server.", e);
            }
        } finally {
            connection.disconnect();
        }
    }

    private String getErrorMessageFromStream(InputStream errorStream) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(errorStream));
        StringBuilder sb = new StringBuilder();
        try {
            String line;
            while ((line = reader.readLine()) != null)
                sb.append(line);

        } catch (Exception e2) {
            logger.log(Level.SEVERE, "Exception while reading the error message from the connection.", e2);
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
}
