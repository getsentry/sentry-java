package io.sentry.internal.debugmeta;

import static io.sentry.util.ClassLoaderUtils.classLoaderOrDefault;
import static io.sentry.util.DebugMetaPropertiesApplier.DEBUG_META_PROPERTIES_FILENAME;

import io.sentry.ILogger;
import io.sentry.SentryLevel;
import java.io.IOException;
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
    List<Properties> debugPropertyList = new ArrayList<>();
    try {
      Enumeration<URL> resourceUrls = classLoader.getResources(DEBUG_META_PROPERTIES_FILENAME);

      while (resourceUrls.hasMoreElements()) {
        URL currenturl = resourceUrls.nextElement();
        Properties currentProperties = new Properties();
        currentProperties.load(currenturl.openStream());
        debugPropertyList.add(currentProperties);
      }

    } catch (IOException e) {
      logger.log(SentryLevel.ERROR, e, "Failed to load %s", DEBUG_META_PROPERTIES_FILENAME);
    } catch (RuntimeException e) {
      logger.log(SentryLevel.ERROR, e, "%s file is malformed.", DEBUG_META_PROPERTIES_FILENAME);
    }

    return debugPropertyList.isEmpty() ? null : debugPropertyList;
  }
}
