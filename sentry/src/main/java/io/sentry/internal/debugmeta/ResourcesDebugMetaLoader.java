package io.sentry.internal.debugmeta;

import static io.sentry.util.ClassLoaderUtils.classLoaderOrDefault;
import static io.sentry.util.DebugMetaPropertiesApplier.DEBUG_META_PROPERTIES_FILENAME;

import io.sentry.ILogger;
import io.sentry.SentryLevel;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class ResourcesDebugMetaLoader implements IDebugMetaLoader {

  private final @NotNull ILogger logger;
  private final @NotNull ClassLoader classLoader;

  public ResourcesDebugMetaLoader(final @NotNull ILogger logger) {
    this(logger, ResourcesDebugMetaLoader.class.getClassLoader());
  }

  ResourcesDebugMetaLoader(final @NotNull ILogger logger, final @Nullable ClassLoader classLoader) {
    this.logger = logger;
    this.classLoader = classLoaderOrDefault(classLoader);
  }

  @Override
  public @Nullable Properties loadDebugMeta() {
    try (final InputStream debugMetaStream =
        classLoader.getResourceAsStream(DEBUG_META_PROPERTIES_FILENAME)) {
      if (debugMetaStream == null) {
        logger.log(SentryLevel.INFO, "%s file was not found.", DEBUG_META_PROPERTIES_FILENAME);
      } else {
        try (final InputStream is = new BufferedInputStream(debugMetaStream)) {
          final Properties properties = new Properties();
          properties.load(is);
          return properties;
        } catch (IOException e) {
          logger.log(SentryLevel.ERROR, e, "Failed to load %s", DEBUG_META_PROPERTIES_FILENAME);
        } catch (RuntimeException e) {
          logger.log(SentryLevel.ERROR, e, "%s file is malformed.", DEBUG_META_PROPERTIES_FILENAME);
        }
      }
    } catch (IOException e) {
      logger.log(SentryLevel.ERROR, e, "Failed to load %s", DEBUG_META_PROPERTIES_FILENAME);
    }

    return null;
  }
}
