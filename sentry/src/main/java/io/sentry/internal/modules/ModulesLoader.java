package io.sentry.internal.modules;

import io.sentry.ILogger;
import io.sentry.SentryLevel;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.TreeMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public abstract class ModulesLoader implements IModulesLoader {

  @SuppressWarnings("CharsetObjectCanBeUsed")
  private static final Charset UTF_8 = Charset.forName("UTF-8");

  public static final String EXTERNAL_MODULES_FILENAME = "sentry-external-modules.txt";
  protected final @NotNull ILogger logger;
  private @Nullable Map<String, String> cachedModules = null;

  public ModulesLoader(final @NotNull ILogger logger) {
    this.logger = logger;
  }

  @Override
  public @Nullable Map<String, String> getOrLoadModules() {
    if (cachedModules != null) {
      return cachedModules;
    }
    cachedModules = loadModules();
    return cachedModules;
  }

  protected abstract Map<String, String> loadModules();

  protected Map<String, String> parseStream(final @NotNull InputStream stream) {
    final Map<String, String> modules = new TreeMap<>();
    try (final BufferedReader reader = new BufferedReader(new InputStreamReader(stream, UTF_8))) {
      String module = reader.readLine();
      while (module != null) {
        int sep = module.lastIndexOf(':');
        final String group = module.substring(0, sep);
        final String version = module.substring(sep + 1);
        modules.put(group, version);
        module = reader.readLine();
      }
      if (logger.isEnabled(SentryLevel.DEBUG)) {
        logger.log(SentryLevel.DEBUG, "Extracted %d modules from resources.", modules.size());
      }
    } catch (IOException e) {
      if (logger.isEnabled(SentryLevel.ERROR)) {
        logger.log(SentryLevel.ERROR, "Error extracting modules.", e);
      }
    } catch (RuntimeException e) {
      if (logger.isEnabled(SentryLevel.ERROR)) {
        logger.log(SentryLevel.ERROR, e, "%s file is malformed.", EXTERNAL_MODULES_FILENAME);
      }
    }
    return modules;
  }
}
