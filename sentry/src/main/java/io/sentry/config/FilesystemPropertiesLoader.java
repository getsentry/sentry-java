package io.sentry.config;

import static io.sentry.SentryLevel.ERROR;

import io.sentry.ILogger;
import io.sentry.SentryLevel;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Loads {@link Properties} from file system location. */
final class FilesystemPropertiesLoader implements PropertiesLoader {
  private final @NotNull String filePath;
  private final @NotNull ILogger logger;

  public FilesystemPropertiesLoader(@NotNull String filePath, @NotNull ILogger logger) {
    this.filePath = filePath;
    this.logger = logger;
  }

  @Override
  public @Nullable Properties load() {
    try {
      final File f = new File(filePath);
      if (f.isFile() && f.canRead()) {
        try (InputStream is = new BufferedInputStream(new FileInputStream(f))) {
          final Properties properties = new Properties();
          properties.load(is);
          return properties;
        }
      }
    } catch (IOException e) {
      if (logger.isEnabled(ERROR)) {
        logger.log(
            SentryLevel.ERROR, e, "Failed to load Sentry configuration from file: %s", filePath);
      }
      return null;
    }
    return null;
  }
}
