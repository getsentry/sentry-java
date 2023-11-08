package io.sentry.android.core.internal.modules;

import android.content.Context;
import io.sentry.ILogger;
import io.sentry.SentryLevel;
import io.sentry.internal.modules.ModulesLoader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.TreeMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class AssetsModulesLoader extends ModulesLoader {

  private final @NotNull Context context;

  public AssetsModulesLoader(final @NotNull Context context, final @NotNull ILogger logger) {
    super(logger);
    this.context = context;
  }

  @Override
  protected Map<String, String> loadModules() {
    final Map<String, String> modules = new TreeMap<>();

    try (final InputStream stream = context.getAssets().open(EXTERNAL_MODULES_FILENAME)) {
      return parseStream(stream);
    } catch (FileNotFoundException e) {
      logger.log(SentryLevel.INFO, "%s file was not found.", EXTERNAL_MODULES_FILENAME);
    } catch (IOException e) {
      logger.log(SentryLevel.ERROR, "Error extracting modules.", e);
    }
    return modules;
  }
}
