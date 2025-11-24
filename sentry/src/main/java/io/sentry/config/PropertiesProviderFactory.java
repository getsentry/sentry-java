package io.sentry.config;

import io.sentry.ILogger;
import io.sentry.SystemOutLogger;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class PropertiesProviderFactory {

  /**
   * Creates a composite properties provider enabling properties resolution from following locations
   * in order:
   *
   * <ul>
   *   <li>system properties
   *   <li>environment variables
   *   <li><code>sentry.properties</code> file which location is resolved from the system property
   *       <code>sentry.properties.file</code>
   *   <li><code>sentry.properties</code> file which location is resolved from the environment
   *       variable <code>SENTRY_PROPERTIES_FILE</code>
   *   <li><code>sentry.properties</code> located in the root of the classpath
   *   <li><code>sentry.properties</code> located in the application's current working directory
   * </ul>
   *
   * @return the properties provider
   */
  public static @NotNull PropertiesProvider create() {
    final ILogger logger = new SystemOutLogger();
    final List<PropertiesProvider> providers = new ArrayList<>();
    providers.add(new SystemPropertyPropertiesProvider());
    providers.add(new EnvironmentVariablePropertiesProvider());

    final String systemPropertyLocation = System.getProperty("sentry.properties.file");
    if (systemPropertyLocation != null) {
      final Properties properties =
          new FilesystemPropertiesLoader(systemPropertyLocation, logger).load();
      if (properties != null) {
        providers.add(new SimplePropertiesProvider(properties));
      }
    }

    final String environmentVariablesLocation = System.getenv("SENTRY_PROPERTIES_FILE");
    if (environmentVariablesLocation != null) {
      final Properties properties =
          new FilesystemPropertiesLoader(environmentVariablesLocation, logger).load();
      if (properties != null) {
        providers.add(new SimplePropertiesProvider(properties));
      }
    }

    final Properties properties = new ClasspathPropertiesLoader(logger).load();
    if (properties != null) {
      providers.add(new SimplePropertiesProvider(properties));
    }

    final Properties runDirectoryProperties =
        new FilesystemPropertiesLoader("sentry.properties", logger, false).load();
    if (runDirectoryProperties != null) {
      providers.add(new SimplePropertiesProvider(runDirectoryProperties));
    }

    return new CompositePropertiesProvider(providers);
  }
}
