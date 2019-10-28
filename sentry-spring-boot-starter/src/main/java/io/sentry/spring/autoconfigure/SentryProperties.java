package io.sentry.spring.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URL;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Configuration for Sentry, example:
 *
 * <pre>
 * sentry:
 *     enabled: true
 *     init-default-client: true
 *     dsn: https://00059966e6224d03a77ea5eca10fbe18@sentry.mycompany.com/14
 *     release: "1.0.1"
 *     dist: x86
 *     environment: staging
 *     server-name: megaServer
 *     tags:
 *         firstTag: Hello
 *         secondTag: Awesome
 *     mdc-tags: [mdcTagA, mdcTagB]
 *     extra:
 *         extraTag: extra
 *     options:
 *         stacktrace.app.packages: com.mycompany,com.other.name
 *         sample.rate: 0.75
 *         uncaught.handler.enabled: false
 * </pre>
 */
@ConfigurationProperties("sentry")
public class SentryProperties {

    /**
     * Whether to enable sentry.
     */
    private boolean enabled = true;

    /**
     * Whether to initialize Sentry Client as a bean.
     */
    private boolean initDefaultClient = true;

    /**
     * Data source name
     * All of the options can be configured by setting querystring parameters on the DSN itself.
     * https://docs.sentry.io/clients/java/config/#configuration-via-the-dsn
     * More information about configuration via DSN https://docs.sentry.io/clients/java/config/#configuration-via-the-dsn
     */
    private URL dsn;

    /**
     * The application version that will be sent with each event.
     */
    private String release;

    /**
     * The application distribution that will be sent with each event.
     * Note that the distribution is only useful (and used) if the release is also set.
     */
    private String dist;

    /**
     * The application environment that will be sent with each event.
     */
    private String environment;

    /**
     * The server name that will be sent with each event.
     */
    private String serverName;

    /**
     * Tags that will be sent with each event.
     */
    private Map<String, String> tags = new LinkedHashMap<>();

    /**
     * Set tag names that are extracted from the SLF4J MDC system.
     */
    private Set<String> mdcTags = new HashSet<>();

    /**
     * Set extra data that will be sent with each event (but not as tags).
     */
    private Map<String, Object> extra = new LinkedHashMap<>();

    /**
     * Additional options, check the <a href="https://docs.sentry.io/clients/java/config/">documentation</a>.
     */
    private Map<String, String> options = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isInitDefaultClient() {
        return initDefaultClient;
    }

    public void setInitDefaultClient(boolean initDefaultClient) {
        this.initDefaultClient = initDefaultClient;
    }

    public URL getDsn() {
        return dsn;
    }

    public void setDsn(URL dsn) {
        this.dsn = dsn;
    }

    public String getRelease() {
        return release;
    }

    public void setRelease(String release) {
        this.release = release;
    }

    public String getDist() {
        return dist;
    }

    public void setDist(String dist) {
        this.dist = dist;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    public void setTags(Map<String, String> tags) {
        this.tags = tags;
    }

    public Set<String> getMdcTags() {
        return mdcTags;
    }

    public void setMdcTags(Set<String> mdcTags) {
        this.mdcTags = mdcTags;
    }

    public Map<String, Object> getExtra() {
        return extra;
    }

    public void setExtra(Map<String, Object> extra) {
        this.extra = extra;
    }

    public Map<String, String> getOptions() {
        return options;
    }

    public void setOptions(Map<String, String> options) {
        this.options = options;
    }

}
