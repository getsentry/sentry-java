package net.kencochrane.raven.getsentry.connection;

import net.kencochrane.raven.connection.HttpConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;

/**
 * Connection to GetSentry.com using the StartCom certificate.
 */
public class GetSentryHttpsConnection extends HttpConnection {
    private static final Logger logger = LoggerFactory.getLogger(GetSentryHttpsConnection.class);
    private static final String GETSENTRY_API_URL = "https://app.getsentry.com/api/%s/store/";
    private static final String CERTIFICATE_PATH = "/startcom/ca.crt";
    private final SSLSocketFactory startcomSslFactory;

    /**
     * Creates an HTTP connection to the GetSentry server.
     *
     * @param projectId identifier of the project.
     * @param publicKey public key of the current project.
     * @param secretKey private key of the current project.
     */
    public GetSentryHttpsConnection(String projectId, String publicKey, String secretKey) {
        super(getSentryUrl(projectId), publicKey, secretKey);
        try {
            this.startcomSslFactory = getStartcomSslFactory();
        } catch (Exception e) {
            throw new IllegalStateException("Couldn't create an SSL Factory for StartCom", e);
        }
    }

    /**
     * Gets the URL to GetSentry for a given projectId.
     *
     * @param projectId Identifier of the project.
     * @return The URL to getSentry for the given project.
     */
    protected static URL getSentryUrl(String projectId) {
        try {
            return new URL(String.format(GETSENTRY_API_URL, projectId));
        } catch (MalformedURLException e) {
            throw new RuntimeException("Couldn't create the URL for API of GetSentry", e);
        }
    }

    @Override
    protected HttpsURLConnection getConnection() {
        HttpsURLConnection connection = (HttpsURLConnection) super.getConnection();
        connection.setSSLSocketFactory(startcomSslFactory);
        return connection;
    }

    /**
     * Create an SSLSocketFactory only able to handle certificates provided by StartCom.
     *
     * @return an SSL factory handling certificates from StartCom.
     * @throws Exception
     */
    private SSLSocketFactory getStartcomSslFactory() throws Exception {
        logger.debug("Loading the certificate from '{}'", CERTIFICATE_PATH);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        Certificate ca = cf.generateCertificate(GetSentryHttpsConnection.class.getResourceAsStream(CERTIFICATE_PATH));
        KeyStore ks = KeyStore.getInstance("jks");

        ks.load(null, null);
        ks.setCertificateEntry("ca", ca);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
        tmf.init(ks);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, tmf.getTrustManagers(), null);

        return sslContext.getSocketFactory();
    }
}
