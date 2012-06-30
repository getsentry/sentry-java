package net.kencochrane.raven;

import org.apache.commons.lang.StringUtils;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

import static org.apache.commons.lang.StringUtils.defaultString;

/**
 * The Sentry DSN is the string you can copy-paste from the Sentry client configuration screen.
 * <p>
 * Use this class to extract the interesting parts from the DSN.
 * </p>
 * <p>
 * To provide flexible configuration, you can specify configuration options though a query string. For example, to
 * enable the sending of the (deprecated) HMAC signature to Sentry, your DSN should look a bit like this:
 * </p>
 * <pre><code>http://public:private@host:port/path/projectid?raven.includeSignature=true</code></pre>
 * <p>
 * The options that are taken into account depend on the selected transport layer.
 * </p>
 *
 * @see Transport.Option#INCLUDE_SIGNATURE
 * @see Transport.Http.Option#TIMEOUT
 */
public class SentryDsn {

    /**
     * The scheme, e.g. http, https or udp.
     */
    public final String scheme;

    /**
     * The scheme variants, e.g. in case of a full scheme of "naive+https" this will hold "naive".
     */
    public final String[] variants;

    /**
     * The Sentry host.
     */
    public final String host;

    /**
     * The public key of the client.
     */
    public final String publicKey;

    /**
     * The secret, private key of the client.
     */
    public final String secretKey;

    /**
     * Optional extra path.
     */
    public final String path;

    /**
     * The id of the project to log to in Sentry.
     */
    public final String projectId;

    /**
     * The server port.
     */
    public final int port;

    /**
     * Extra Raven client options.
     */
    public final Map<String, String> options;

    public SentryDsn(String scheme, String[] variants, String host, String publicKey, String secretKey, String path, String projectId, int port, Map<String, String> options) {
        this.scheme = scheme;
        this.variants = (variants == null ? new String[0] : variants);
        this.host = host;
        this.publicKey = publicKey;
        this.secretKey = secretKey;
        this.path = path;
        this.projectId = projectId;
        this.port = port;
        if (options == null) {
            this.options = Collections.emptyMap();
        } else {
            this.options = Collections.unmodifiableMap(options);
        }
    }

    public boolean getOptionAsBoolean(String key, boolean defaultValue) {
        String value = options.get(key);
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    public int getOptionAsInt(String key, int defaultValue) {
        String value = options.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new RuntimeException("Expected " + key + " to be a valid int");
        }
    }

    public boolean isVariantIncluded(String variant) {
        return Arrays.binarySearch(variants, variant) >= 0;
    }

    public String getFullScheme(String... excludes) {
        Set<String> excludedSchemes = Collections.emptySet();
        if (excludes != null && excludes.length > 0) {
            excludedSchemes = new HashSet<String>(Arrays.asList(excludes));
        }
        String full = "";
        List<String> parts = new LinkedList<String>();
        for (String variant : variants) {
            if (!excludedSchemes.contains(variant)) {
                parts.add(variant);
            }
        }
        parts.add(scheme);
        return StringUtils.join(parts, '+');
    }

    public String toString(boolean full) {
        String protocol = (!full || variants.length == 0 ? scheme : StringUtils.join(variants, '+') + "+" + scheme);
        String fullHost = (port < 0 ? host : host + ":" + port);
        String fullPath = (path == null ? "" : path);
        if (!full) {
            return String.format("%s://%s%s", protocol, fullHost, fullPath);
        }
        fullPath += "/" + projectId;
        String user = (StringUtils.isBlank(secretKey) ? publicKey : publicKey + ":" + secretKey);
        return String.format("%s://%s@%s%s", protocol, user, fullHost, fullPath);
    }

    @Override
    public String toString() {
        return toString(true);
    }

    public static SentryDsn build() {
        return build(null, DefaultLookUps.values(), null);
    }

    public static SentryDsn build(String fullDsn) {
        return build(fullDsn, DefaultLookUps.values(), null);
    }

    public static SentryDsn build(String fullDsn, LookUp[] overrides, LookUp[] fallbacks) {
        String dsn = defaultString(firstResult(overrides), defaultString(fullDsn, firstResult(fallbacks)));
        int schemeEnd = dsn.indexOf("://");
        if (schemeEnd <= 0) {
            throw new InvalidDsnException("Expected to discover a scheme in the Sentry DSN");
        }
        String fullScheme = fullDsn.substring(0, schemeEnd);
        String[] schemeParts = StringUtils.split(fullScheme, '+');
        if (schemeParts.length > 2) {
            throw new InvalidDsnException("Scheme " + fullScheme + " is not supported");
        }
        String scheme = fullScheme;
        String[] variants = null;
        if (schemeParts.length > 1) {
            variants = Arrays.copyOfRange(schemeParts, 0, schemeParts.length - 1);
            scheme = schemeParts[schemeParts.length - 1];
        }
        try {
            // To prevent us from having to register a handler for for example udp URLs, we'll replace the original
            // scheme with "http" and let the URL code of Java handle the parsing.
            URL url = new URL("http" + dsn.substring(schemeEnd));
            String[] userParts = url.getUserInfo().split(":");
            String publicKey = userParts[0];
            String secretKey = null;
            if (userParts.length > 1) {
                secretKey = userParts[1];
            }
            String urlPath = url.getPath();
            int lastSlash = urlPath.lastIndexOf('/');
            String path = urlPath.substring(0, lastSlash);
            String projectId = urlPath.substring(lastSlash + 1);
            Map<String, String> options = parseQueryString(url.getQuery());
            return new SentryDsn(scheme, variants, url.getHost(), publicKey, secretKey, path, projectId, url.getPort(), options);
        } catch (MalformedURLException e) {
            // This exception should only be thrown when an unhandled scheme slips in which the above code should
            // prevent. Nevertheless: throw something.
            throw new InvalidDsnException("Failed to parse " + dsn, e);
        }
    }

    /**
     * Parses simple query strings.
     * <p>
     * We don't expect complex query strings - they are only used to pass in Raven options.
     * </p>
     *
     * @param q the query string
     * @return the key/value pairs in the query string
     */
    protected static Map<String, String> parseQueryString(String q) {
        Map<String, String> map = new HashMap<String, String>();
        String[] pairs = StringUtils.split(q, '&');
        if (pairs == null) {
            return map;
        }
        for (String pair : pairs) {
            String[] components = StringUtils.split(pair, '=');
            String value = (components.length == 1 ? null : StringUtils.join(components, '=', 1, components.length));
            map.put(components[0], value);
        }
        return map;
    }

    public static String firstResult(LookUp[] lookups) {
        if (lookups == null) {
            return null;
        }
        for (LookUp lookup : lookups) {
            String dsn = lookup.findDsn();
            if (!StringUtils.isBlank(dsn)) {
                return dsn;
            }
        }
        return null;
    }

    public static class InvalidDsnException extends RuntimeException {

        public InvalidDsnException(String message) {
            super(message);
        }

        public InvalidDsnException(String message, Throwable t) {
            super(message, t);
        }

    }

    public interface LookUp {

        String findDsn();

    }

    public enum DefaultLookUps implements LookUp {

        ENV {
            @Override
            public String findDsn() {
                return System.getenv("SENTRY_DSN");
            }
        },

        SYSTEM_PROPERTY {
            @Override
            public String findDsn() {
                return System.getProperty("SENTRY_DSN");
            }
        }


    }

}
