package io.sentry.android.core.internal.modules;

import android.content.Context;
import io.sentry.ILogger;
import io.sentry.SentryLevel;
import io.sentry.internal.modules.IModulesLoader;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.TreeMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class AssetsModulesLoader implements IModulesLoader {

  private @Nullable Map<String, String> cachedModules = null;
  private final @NotNull ILogger logger;
  private final @NotNull WeakReference<Context> contextRef;

  public AssetsModulesLoader(final @NotNull Context context, final @NotNull ILogger logger) {
    this.logger = logger;
    this.contextRef = new WeakReference<>(context);
  }

  @Override public @Nullable Map<String, String> getOrLoadModules() {
    if (cachedModules != null) {
      return cachedModules;
    }
    cachedModules = extractModules();
    return cachedModules;
  }

  @SuppressWarnings("CharsetObjectCanBeUsed")
  private Map<String, String> extractModules() {
    final Map<String, String> modules = new TreeMap<>();
    try {
      final Context context = contextRef.get();
      if (context == null) {
        return modules;
      }

      try (final BufferedReader reader =
             new BufferedReader(new InputStreamReader(context.getAssets().open("sentry-external-modules.txt"), Charset.forName("UTF-8")))) {
        String module = reader.readLine();
        while (module != null) {
          int sep = module.lastIndexOf(':');
          final String group = module.substring(0, sep);
          final String version = module.substring(sep + 1);
          modules.put(group, version);
          module = reader.readLine();
        }
        logger.log(SentryLevel.DEBUG, "Extracted %d modules from resources.", modules.size());
      } catch (FileNotFoundException e) {
        logger.log(SentryLevel.INFO, "sentry-external-modules.txt file was not found.");
      } catch (IOException e) {
        logger.log(SentryLevel.ERROR, "Error extracting modules.", e);
      } catch (RuntimeException e) {
        logger.log(SentryLevel.ERROR, "sentry-external-modules.txt file is malformed.", e);
      }
    } catch (SecurityException e) {
      logger.log(SentryLevel.INFO, "Access to resources denied.", e);
    }
    return modules;
  }
}
