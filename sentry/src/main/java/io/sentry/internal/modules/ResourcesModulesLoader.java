package io.sentry.internal.modules;

import io.sentry.ILogger;
import io.sentry.SentryLevel;
import java.io.InputStream;
import java.util.Map;
import java.util.TreeMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class ResourcesModulesLoader extends ModulesLoader {

  public ResourcesModulesLoader(final @NotNull ILogger logger) {
    super(logger);
  }

  @Override protected Map<String, String> loadModules() {
    final Map<String, String> modules = new TreeMap<>();
    try {
      final InputStream resourcesStream =
        getClass().getClassLoader().getResourceAsStream(EXTERNAL_MODULES_FILENAME);

      if (resourcesStream == null) {
        logger.log(SentryLevel.DEBUG, "%s file was not found.", EXTERNAL_MODULES_FILENAME);
        return modules;
      }

      return parseStream(resourcesStream);
    } catch (SecurityException e) {
      logger.log(SentryLevel.INFO, "Access to resources denied.", e);
    }
    return modules;
  }
}
