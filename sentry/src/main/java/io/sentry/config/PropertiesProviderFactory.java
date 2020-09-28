package io.sentry.config;

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
   * </ul>
   *
   * @return the properties provider
   */
  public static @NotNull PropertiesProvider create() {
    final List<PropertiesProvider> providers = new ArrayList<>();
    providers.add(new SystemPropertyPropertiesProvider());
    providers.add(new EnvironmentVariablePropertiesProvider());

    final String systemPropertyLocation = System.getProperty("sentry.properties.file");
    if (systemPropertyLocation != null) {
      final Properties properties = new FilesystemPropertiesLoader(systemPropertyLocation).load();
      if (properties != null) {
        providers.add(new SimplePropertiesProvider(properties));
      }
    }

    final String environmentVariablesLocation = System.getenv("SENTRY_PROPERTIES_FILE");
    if (environmentVariablesLocation != null) {
      final Properties properties =
          new FilesystemPropertiesLoader(environmentVariablesLocation).load();
      if (properties != null) {
        providers.add(new SimplePropertiesProvider(properties));
      }
    }

    final Properties properties = new ClasspathPropertiesLoader().load();
    if (properties != null) {
      providers.add(new SimplePropertiesProvider(properties));
    }

    return new CompositePropertiesProvider(providers);
  }
}
