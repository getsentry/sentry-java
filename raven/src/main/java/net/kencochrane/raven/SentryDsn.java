package net.kencochrane.raven;

import org.apache.commons.lang.StringUtils;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

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
     * Logger.
     */
    private static final Logger LOG = Logger.getLogger("raven.client");

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

    /**
     * Constructor for your convenience.
     * <p>
     * It's recommended to use one of the {@link #build()} or {@link #buildOptional()} methods instead.
     * </p>
     *
     * @param scheme    scheme
     * @param variants  scheme variants (e.g. naive, async)
     * @param host      host
     * @param publicKey public key
     * @param secretKey private key
     * @param path      path
     * @param projectId project id
     * @param port      the port
     * @param options   miscellaneous options
     */
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

    /**
     * Gets the value of an option as a boolean.
     * <p>
     * In case no option is specified, the default value is returned.
     * </p>
     *
     * @param key          key of the option
     * @param defaultValue default value to return when no matching option value was found
     * @return the value of the option or the default value when absent
     */
    public boolean getOptionAsBoolean(String key, boolean defaultValue) {
        String value = options.get(key);
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    /**
     * Gets the value of an option as an int.
     *
     * @param key          key of the option
     * @param defaultValue value to return when the option was not specified
     * @return the value of the option or the default value when absent
     */
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

    /**
     * Checks whether the scheme variant is specified in this dsn.
     *
     * @param variant variant
     * @return <code>true</code> when the variant was specified
     */
    public boolean isVariantIncluded(String variant) {
        return Arrays.binarySearch(variants, variant) >= 0;
    }

    /**
     * Gets the full scheme, optionally excluding some parts.
     * <p>
     * This allows the async transport layer to get the actual underlying transport layer to use, ignoring the async
     * variant.
     * </p>
     *
     * @param excludes variants to exclude from the full scheme
     * @return the full scheme
     */
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

    /**
     * Returns the full dsn or the derived dsn typically used for transport.
     *
     * @param full whether to generate the full or the derived dsn
     * @return the full or derived dsn
     */
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

    /**
     * Builds the Sentry dsn based on the default lookups as specified by {@link DefaultLookUps}.
     * <p>
     * PaaS providers such as Heroku prefer environment variables. This method will first examine the environment and
     * then the system properties to find a Sentry dsn value.
     * </p>
     *
     * @return the Sentry dsn when found
     */
    public static SentryDsn build() {
        return build(null, DefaultLookUps.values(), null);
    }

    /**
     * Performs the same logic as {@link #build()} but will catch any {@link InvalidDsnException} thrown and return null
     * instead.
     *
     * @return the Sentry dsn when found and valid or <code>null</code> instead
     */
    public static SentryDsn buildOptional() {
        try {
            return build();
        } catch (SentryDsn.InvalidDsnException e) {
            LOG.log(Level.WARNING, "Could not automatically determine a valid DSN; client will be disabled", e);
            return null;
        }
    }

    /**
     * Builds the Sentry dsn.
     * <p>
     * In case a Sentry dsn is specified in the environment or system properties, that value takes precedence over the
     * <code>fullDsn</code> parameter.
     * </p>
     *
     * @param fullDsn dsn
     * @return the dsn found in either the environment, system properties or derived from the parameter <code>fullDsn</code>
     */
    public static SentryDsn build(String fullDsn) {
        return build(fullDsn, DefaultLookUps.values(), null);
    }

    /**
     * Performs the same logic as {@link #build(String)} but will catch any {@link InvalidDsnException} thrown and
     * return null instead.
     *
     * @return the dsn found in either the environment, system properties or derived from the parameter
     *         <code>fullDsn</code>; if no valid dsn is available, this will return <code>null</code>
     */
    public static SentryDsn buildOptional(String fullDsn) {
        try {
            return build(fullDsn);
        } catch (SentryDsn.InvalidDsnException e) {
            LOG.log(Level.WARNING, "Could not automatically determine a valid DSN; client will be disabled", e);
            return null;
        }
    }

    /**
     * Builds the dsn.
     * <p>
     * The overrides take precedence over the dsn parameter, while the fallbacks provide a way to look for the dsn when
     * the dsn parameter was empty.
     * </p>
     *
     * @param fullDsn   the supplied dsn
     * @param overrides places to check for a dsn value before using the supplied dsn
     * @param fallbacks places to check for a dsn value when the supplied dsn is an empty or null string
     * @return the built Sentry dsn
     */
    public static SentryDsn build(final String fullDsn, LookUp[] overrides, LookUp[] fallbacks) {
        String dsn = defaultString(firstResult(overrides), defaultString(fullDsn, firstResult(fallbacks)));
        if (StringUtils.isBlank(dsn)) {
            throw new InvalidDsnException("No valid Sentry DSN found");
        }
        int schemeEnd = dsn.indexOf("://");
        if (schemeEnd <= 0) {
            throw new InvalidDsnException("Expected to discover a scheme in the Sentry DSN");
        }
        String fullScheme = dsn.substring(0, schemeEnd);
        String[] schemeParts = StringUtils.split(fullScheme, '+');
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
     * See {@link #build(String, net.kencochrane.raven.SentryDsn.LookUp[], net.kencochrane.raven.SentryDsn.LookUp[])}.
     * This method will return <code>null</code> when no valid DSN was found instead of throwing a
     * {@link InvalidDsnException}.
     *
     * @param fullDsn   the supplied dsn
     * @param overrides places to check for a dsn value before using the supplied dsn
     * @param fallbacks places to check for a dsn value when the supplied dsn is an empty or null string
     * @return the built Sentry dsn or <code>null</code> when no such value was found
     */
    public static SentryDsn buildOptional(final String fullDsn, LookUp[] overrides, LookUp[] fallbacks) {
        try {
            return build(fullDsn, overrides, fallbacks);
        } catch (SentryDsn.InvalidDsnException e) {
            LOG.log(Level.WARNING, "Could not automatically determine a valid DSN; client will be disabled", e);
            return null;
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
                return System.getenv(Utils.SENTRY_DSN);
            }
        },

        SYSTEM_PROPERTY {
            @Override
            public String findDsn() {
                return System.getProperty(Utils.SENTRY_DSN);
            }
        }


    }

}
