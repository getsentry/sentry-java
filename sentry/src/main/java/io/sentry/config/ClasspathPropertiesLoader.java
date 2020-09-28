package io.sentry.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Loads {@link Properties} from the `sentry.properties` file located on the classpath. */
final class ClasspathPropertiesLoader implements PropertiesLoader {
  private final @NotNull String fileName;
  private final @NotNull ClassLoader classLoader;

  public ClasspathPropertiesLoader(@NotNull String fileName, @NotNull ClassLoader classLoader) {
    this.fileName = fileName;
    this.classLoader = classLoader;
  }

  public ClasspathPropertiesLoader() {
    this("sentry.properties", ClasspathPropertiesLoader.class.getClassLoader());
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
      // TODO: log error
      return null;
    }
    return null;
  }
}
