package io.sentry.config.provider;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Properties;

import io.sentry.config.ResourceLoader;
import io.sentry.util.Nullable;

/**
 * A configuration provider that loads the properties from a {@link ResourceLoader}.
 */
public class ResourceLoaderConfigurationProvider implements ConfigurationProvider {
    @Nullable
    private final Properties properties;

    /**
     * Instantiates a new resource loader based configuration provider.
     * @param rl the resource loader used to load the configuration file
     * @param filePath the path to the configuration file as understood by the resource loader
     * @param charset the charset of the configuration file
     * @throws IOException on failure to process the configuration file contents
     */
    public ResourceLoaderConfigurationProvider(ResourceLoader rl, @Nullable String filePath, Charset charset)
            throws IOException {
        properties = loadProperties(rl, filePath, charset);
    }

    @Nullable
    private static Properties loadProperties(ResourceLoader rl, @Nullable String filePath, Charset charset)
            throws IOException {
        if (filePath == null) {
            return null;
        }

        InputStream is = rl.getInputStream(filePath);

        if (is == null) {
            return null;
        }

        try (InputStreamReader rdr = new InputStreamReader(is, charset)) {
            Properties props = new Properties();
            props.load(rdr);
            return props;
        }
    }

    @Override
    public String getProperty(String key) {
        return properties == null ? null : properties.getProperty(key);
    }
}
