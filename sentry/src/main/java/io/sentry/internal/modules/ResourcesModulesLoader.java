package io.sentry.internal.modules;

import static io.sentry.util.ClassLoaderUtils.classLoaderOrDefault;

import io.sentry.ILogger;
import io.sentry.SentryLevel;
import java.io.InputStream;
import java.util.Map;
import java.util.TreeMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class ResourcesModulesLoader extends ModulesLoader {

  private final @NotNull ClassLoader classLoader;

  public ResourcesModulesLoader(final @NotNull ILogger logger) {
    this(logger, ResourcesModulesLoader.class.getClassLoader());
  }

  ResourcesModulesLoader(final @NotNull ILogger logger, final @Nullable ClassLoader classLoader) {
    super(logger);
    this.classLoader = classLoaderOrDefault(classLoader);
  }

  @Override
  protected Map<String, String> loadModules() {
    final Map<String, String> modules = new TreeMap<>();
    try {
      final InputStream resourcesStream =
          classLoader.getResourceAsStream(EXTERNAL_MODULES_FILENAME);

      if (resourcesStream == null) {
        logger.log(SentryLevel.INFO, "%s file was not found.", EXTERNAL_MODULES_FILENAME);
        return modules;
      }

      return parseStream(resourcesStream);
    } catch (SecurityException e) {
      logger.log(SentryLevel.INFO, "Access to resources denied.", e);
    }
    return modules;
  }
}
