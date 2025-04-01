package io.sentry.internal.debugmeta;

import static io.sentry.SentryLevel.ERROR;
import static io.sentry.SentryLevel.INFO;
import static io.sentry.util.ClassLoaderUtils.classLoaderOrDefault;
import static io.sentry.util.DebugMetaPropertiesApplier.DEBUG_META_PROPERTIES_FILENAME;

import io.sentry.ILogger;
import io.sentry.SentryLevel;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
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
  public @Nullable List<Properties> loadDebugMeta() {
    final @NotNull List<Properties> debugPropertyList = new ArrayList<>();
    try {
      final @NotNull Enumeration<URL> resourceUrls =
          classLoader.getResources(DEBUG_META_PROPERTIES_FILENAME);

      while (resourceUrls.hasMoreElements()) {
        final @NotNull URL currentUrl = resourceUrls.nextElement();
        try (final InputStream is = currentUrl.openStream()) {
          final @NotNull Properties currentProperties = new Properties();
          currentProperties.load(is);
          debugPropertyList.add(currentProperties);
          if (logger.isEnabled(INFO)) {
            logger.log(SentryLevel.INFO, "Debug Meta Data Properties loaded from %s", currentUrl);
          }
        } catch (RuntimeException e) {
          if (logger.isEnabled(ERROR)) {
            logger.log(SentryLevel.ERROR, e, "%s file is malformed.", currentUrl);
          }
        }
      }
    } catch (IOException e) {
      if (logger.isEnabled(ERROR)) {
        logger.log(SentryLevel.ERROR, e, "Failed to load %s", DEBUG_META_PROPERTIES_FILENAME);
      }
    }

    if (debugPropertyList.isEmpty()) {
      if (logger.isEnabled(INFO)) {
        logger.log(SentryLevel.INFO, "No %s file was found.", DEBUG_META_PROPERTIES_FILENAME);
      }
      return null;
    }

    return debugPropertyList;
  }
}
