package io.sentry.config;

import java.io.InputStream;

/**
 * A {@link ResourceLoader} that considers the paths to be resource locations in the context classloader of the current
 * thread.
 */
public class ContextClassLoaderResourceLoader implements ResourceLoader {
    @Override
    public InputStream getInputStream(String filepath) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return classLoader.getResourceAsStream(filepath);
    }
}
