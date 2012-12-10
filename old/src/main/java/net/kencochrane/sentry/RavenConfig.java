package net.kencochrane.sentry;

import java.net.*;

/**
 * User: ken cochrane
 * Date: 2/8/12
 * Time: 1:16 PM
 */
public class RavenConfig {

    private String host, protocol, publicKey, secretKey, path, projectId;
    private int port;
    private String proxyType, proxyHost;
    private int proxyPort;
    private boolean naiveSsl;

    /**
     * Takes in a sentryDSN and builds up the configuration
     *
     * @param sentryDSN '{PROTOCOL}://{PUBLIC_KEY}:{SECRET_KEY}@{HOST}/{PATH}/{PROJECT_ID}'
     */
    public RavenConfig(String sentryDSN) {
        this(sentryDSN, null, false);
    }

    /**
     * Takes in a sentryDSN and builds up the configuration
     *
     * @param sentryDSN '{PROTOCOL}://{PUBLIC_KEY}:{SECRET_KEY}@{HOST}/{PATH}/{PROJECT_ID}'
     * @param proxy     proxy to use for the HTTP connections; blank or null when no proxy is to be used
     * @param naiveSsl  use a hostname verifier for SSL connections that allows all connections
     */
    public RavenConfig(String sentryDSN, String proxy, boolean naiveSsl) {
        this.naiveSsl = naiveSsl;
        try {
            boolean udp = sentryDSN.startsWith("udp://");
            if (udp) {
                // So either we have to start registering protocol handlers which is a PITA to do decently in Java
                // without causing problems for the actual application, or we hack our way around it.
                sentryDSN = sentryDSN.replace("udp://", "http://");
            }
            URL url = new URL(sentryDSN);
            this.host = url.getHost();
            this.protocol = udp ? "udp" : url.getProtocol();
            String urlPath = url.getPath();

            int lastSlash = urlPath.lastIndexOf("/");
            this.path = urlPath.substring(0, lastSlash);
            // ProjectId is the integer after the last slash in the path
            this.projectId = urlPath.substring(lastSlash + 1);

            String userInfo = url.getUserInfo();
            String[] userParts = userInfo.split(":");

            this.secretKey = userParts[1];
            this.publicKey = userParts[0];

            this.port = url.getPort();

            if (proxy != null && !proxy.isEmpty()) {
                String[] proxyParts = proxy.split(":");
                this.proxyType = proxyParts[0];
                this.proxyHost = proxyParts[1];
                this.proxyPort = Integer.parseInt(proxyParts[2]);
            }

        } catch (MalformedURLException e) {
            e.printStackTrace();
        }

    }

    /**
     * The Sentry server URL that we post the message to.
     *
     * @return sentry server url
     */
    public String getSentryURL() {
        StringBuilder serverUrl = new StringBuilder();
        serverUrl.append(getProtocol());
        serverUrl.append("://");
        serverUrl.append(getHost());
        if ((getPort() != 0) && (getPort() != 80) && getPort() != -1) {
            serverUrl.append(":").append(getPort());
        }
        serverUrl.append(getPath());
        serverUrl.append("/api/store/");
        return serverUrl.toString();
    }

    public Proxy getProxy() {
        if (proxyType == null || Proxy.Type.DIRECT.name().equals(proxyType)) {
            return Proxy.NO_PROXY;
        }
        SocketAddress proxyAddress = new InetSocketAddress(proxyHost, proxyPort);
        return new Proxy(Proxy.Type.valueOf(proxyType), proxyAddress);
    }

    /**
     * The sentry server host
     *
     * @return server host
     */
    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    /**
     * Sentry server protocol http https?
     *
     * @return http or https
     */
    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    /**
     * The Sentry public key
     *
     * @return Sentry public key
     */
    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    /**
     * The Sentry secret key
     *
     * @return Sentry secret key
     */
    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    /**
     * sentry url path
     *
     * @return url path
     */
    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    /**
     * Sentry project Id
     *
     * @return project Id
     */
    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    /**
     * sentry server port
     *
     * @return server port
     */
    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean isNaiveSsl() {
        return naiveSsl;
    }

    public void setNaiveSsl(boolean naiveSsl) {
        this.naiveSsl = naiveSsl;
    }

    @Override
    public String toString() {
        return "RavenConfig{" +
                "host='" + host + '\'' +
                ", protocol='" + protocol + '\'' +
                ", publicKey='" + publicKey + '\'' +
                ", secretKey='" + secretKey + '\'' +
                ", path='" + path + '\'' +
                ", projectId='" + projectId + '\'' +
                ", naiveSsl='" + naiveSsl + '\'' +
                ", SentryUrl='" + getSentryURL() + '\'' +
                '}';
    }

}
