package io.sentry.config;

import io.sentry.ILogger;
import io.sentry.SentryLevel;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Loads {@link Properties} from the `sentry.properties` file located on the classpath. */
final class ClasspathPropertiesLoader implements PropertiesLoader {
  private final @NotNull String fileName;
  private final @NotNull ClassLoader classLoader;
  private final @NotNull ILogger logger;

  public ClasspathPropertiesLoader(
      @NotNull String fileName, @NotNull ClassLoader classLoader, @NotNull ILogger logger) {
    this.fileName = fileName;
    this.classLoader = classLoader;
    this.logger = logger;
  }

  public ClasspathPropertiesLoader(@NotNull ILogger logger) {
    this("sentry.properties", ClasspathPropertiesLoader.class.getClassLoader(), logger);
  }

  @Override
  public @Nullable Properties load() {
    try (InputStream inputStream = classLoader.getResourceAsStream(fileName)) {
      if (inputStream != null) {
        final Properties properties = new Properties();
        properties.load(inputStream);
        return properties;
      }
    } catch (IOException e) {
      logger.log(
          SentryLevel.ERROR,
          e,
          "Failed to load Sentry configuration from classpath resource: %s",
          fileName);
      return null;
    }
    return null;
  }
}
