package net.kencochrane.raven;

import net.kencochrane.raven.exception.InvalidDsnException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/**
 * Data Source name allowing a direct connection to a Sentry server.
 */
public class Dsn {
    /**
     * Name of the environment or system variable containing the DSN.
     */
    public static final String DSN_VARIABLE = "SENTRY_DSN";
    /**
     * Option specific to raven-java, allowing to disable the compression of requests to the Sentry Server.
     */
    public static final String NOCOMPRESSION_OPTION = "raven.nocompression";
    /**
     * Option specific to raven-java, allowing to set a timeout (in ms) for a request to the Sentry server.
     */
    public static final String TIMEOUT_OPTION = "raven.timeout";
    /**
     * Option to send events asynchronously.
     */
    public static final String ASYNC_OPTION = "raven.async";
    /**
     * Option to set the charset for strings sent to sentry.
     */
    public static final String CHARSET_OPTION = "raven.charset";
    /**
     * Protocol setting to disable security checks over an SSL connection.
     */
    public static final String NAIVE_PROTOCOL = "naive";
    private String secretKey;
    private String publicKey;
    private String projectId;
    private String protocol;
    private String host;
    private int port;
    private String path;
    private Set<String> protocolSettings;
    private Map<String, String> options;
    private URI uri;

    /**
     * Creates a DS based on a String.
     *
     * @param dsn dsn in a string form.
     */
    public Dsn(String dsn) {
        options = new HashMap<String, String>();
        protocolSettings = new HashSet<String>();

        URI dsnUri = URI.create(dsn);
        extractProtocolInfo(dsnUri);
        extractUserKeys(dsnUri);
        extractHostInfo(dsnUri);
        extractPathInfo(dsnUri);
        extractOptions(dsnUri);

        makeOptionsImmutable();

        validate();

        try {
            uri = new URI(protocol, null, host, port, path, null, null);
        } catch (URISyntaxException e) {
            throw new InvalidDsnException("Impossible to determine Sentry's URI from the DSN '" + dsn + "'", e);
        }
    }

    private void extractPathInfo(URI uri) {
        String uriPath = uri.getPath();
        if (uriPath == null)
            return;
        int projectIdStart = uriPath.lastIndexOf("/") + 1;
        path = uriPath.substring(0, projectIdStart);
        projectId = uriPath.substring(projectIdStart);
    }

    private void extractHostInfo(URI uri) {
        host = uri.getHost();
        port = uri.getPort();
    }

    private void extractProtocolInfo(URI uri) {
        String scheme = uri.getScheme();
        if (scheme == null)
            return;
        String[] schemeDetails = scheme.split("\\+");
        protocolSettings.addAll(Arrays.asList(schemeDetails).subList(0, schemeDetails.length - 1));
        protocol = schemeDetails[schemeDetails.length - 1];
    }

    private void extractUserKeys(URI uri) {
        String userInfo = uri.getUserInfo();
        if (userInfo == null)
            return;
        String[] userDetails = userInfo.split(":");
        publicKey = userDetails[0];
        if (userDetails.length > 1)
            secretKey = userDetails[1];
    }

    private void extractOptions(URI uri) {
        String query = uri.getQuery();
        if (query == null)
            return;
        String[] optionPairs = query.split("&");
        for (String optionPair : optionPairs) {
            String[] pairDetails = optionPair.split("=");
            options.put(pairDetails[0], (pairDetails.length > 1) ? pairDetails[1] : "");
        }
    }

    private void makeOptionsImmutable() {
        // Make the options immutable
        options = Collections.unmodifiableMap(options);
        protocolSettings = Collections.unmodifiableSet(protocolSettings);
    }

    private void validate() {
        List<String> missingElements = new LinkedList<String>();
        if (host == null)
            missingElements.add("host");
        if (publicKey == null)
            missingElements.add("public key");
        if (secretKey == null)
            missingElements.add("secret key");
        if (projectId == null || projectId.isEmpty())
            missingElements.add("project ID");

        if (!missingElements.isEmpty())
            throw new InvalidDsnException("Invalid DSN, the following properties aren't set '" + missingElements + "'");
    }

    public String getSecretKey() {
        return secretKey;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getProtocol() {
        return protocol;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getPath() {
        return path;
    }

    public Set<String> getProtocolSettings() {
        return protocolSettings;
    }

    public Map<String, String> getOptions() {
        return options;
    }

    /**
     * Creates the URI of the sentry server.
     *
     * @return the URI of the sentry server.
     */
    public URI getUri() {
        return uri;
    }
}
