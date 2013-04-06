package net.kencochrane.raven.connection;

import java.net.URI;
import java.util.*;

/**
 * Data Source name allowing a direct connection to a Sentry server.
 */
public class Dsn {
    /**
     * Option specific to raven-java, allowing to enable/disable the compression of the requests to the Sentry Server.
     */
    public static final String COMPRESSION_OPTION = "raven.compression";
    /**
     * Option specific to raven-java, allowing to set a timeout (in ms) for a request to the Sentry server.
     */
    public static final String TIMEOUT_OPTION = "raven.timeout";
    /**
     * Option to set the charset for strings sent to sentry.
     */
    public static final String CHARSET_OPTION = "raven.charset";
    private String secretKey;
    private String publicKey;
    private String projectId;
    private String protocol;
    private String host;
    private int port;
    private String path;
    private Set<String> protocolSettings;
    private Map<String, String> options;

    /**
     * Creates a DS based on a String.
     *
     * @param dsn dsn in a string form.
     */
    public Dsn(String dsn) {
        options = new HashMap<String, String>();
        protocolSettings = new HashSet<String>();

        URI uri = URI.create(dsn);
        extractProtocolInfo(uri);
        extractUserKeys(uri);
        extractHostInfo(uri);
        extractPathInfo(uri);
        extractOptions(uri);

        makeOptionsImmutable();
    }

    private void extractPathInfo(URI uri) {
        String uriPath = uri.getPath();
        int projectIdStart = uriPath.lastIndexOf("/") + 1;
        path = uriPath.substring(0, projectIdStart);
        projectId = uriPath.substring(projectIdStart);
    }

    private void extractHostInfo(URI uri) {
        host = uri.getHost();
        port = uri.getPort();
    }

    private void extractProtocolInfo(URI uri) {
        String[] schemeDetails = uri.getScheme().split("\\+");
        protocolSettings.addAll(Arrays.asList(schemeDetails).subList(0, schemeDetails.length - 1));
        protocol = schemeDetails[schemeDetails.length - 1];
    }

    private void extractUserKeys(URI uri) {
        String[] userDetails = uri.getUserInfo().split(":");
        publicKey = userDetails[0];
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
     * @throws Exception if an URI couldn't be created.
     */
    //TODO: Return as a String instead?
    //TODO: Exception, really?
    public URI getUri() throws Exception {
        return new URI(protocol, null, host, port, path, null, null);
    }
}
