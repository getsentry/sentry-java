package net.kencochrane.sentry;

import java.net.MalformedURLException;
import java.net.URL;
/**
 * User: ken cochrane
 * Date: 2/8/12
 * Time: 1:16 PM
 */
public class RavenConfig {

    private String host, protocol, publicKey, secretKey, path, projectId;
    private int port;

    public RavenConfig(String sentryDSN) {

    //'{PROTOCOL}://{PUBLIC_KEY}:{SECRET_KEY}@{HOST}/{PATH}{PROJECT_ID}'

        try {
            System.out.println("Sentry DSN = '" + sentryDSN + "' ");
            URL url = new URL(sentryDSN);
            this.host = url.getHost();
            this.protocol = url.getProtocol();
            String urlPath = url.getPath();
            String[] urlParts = urlPath.split("/");
            this.path = urlPath;
            this.projectId = urlParts[1];

            String userInfo =  url.getUserInfo();
            String[] userParts = userInfo.split(":");

            this.secretKey = userParts[1];
            this.publicKey = userParts[0];

            this.port = url.getPort();


        } catch (MalformedURLException e) {
            e.printStackTrace();
        }

    }

    public String getSentryURL(){
        StringBuilder serverUrl = new StringBuilder();
        serverUrl.append(getProtocol());
        serverUrl.append("://");
        serverUrl.append(getHost());
        if ((getPort() != 0) && (getPort() != 80)){
            serverUrl.append(":").append(getPort());
        }
        serverUrl.append("/api/store/");
        return serverUrl.toString();
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
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
                ", SentryUrl='" + getSentryURL() + '\'' +
                '}';
    }

}
