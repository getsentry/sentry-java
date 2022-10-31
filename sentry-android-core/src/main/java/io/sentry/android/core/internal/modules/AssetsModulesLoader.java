package io.sentry.android.core.internal.modules;

import android.content.Context;
import io.sentry.ILogger;
import io.sentry.SentryLevel;
import io.sentry.internal.modules.IModulesLoader;
import io.sentry.internal.modules.ModulesLoader;
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
public final class AssetsModulesLoader extends ModulesLoader {

  private final @NotNull WeakReference<Context> contextRef;

  public AssetsModulesLoader(final @NotNull Context context, final @NotNull ILogger logger) {
    super(logger);
    this.contextRef = new WeakReference<>(context);
  }

  @Override
  protected Map<String, String> loadModules() {
    final Map<String, String> modules = new TreeMap<>();
    final Context context = contextRef.get();
    if (context == null) {
      return modules;
    }

    try {
      final InputStream stream = context.getAssets().open(EXTERNAL_MODULES_FILENAME);
      return parseStream(stream);
    } catch (IOException e) {
      logger.log(SentryLevel.ERROR, "Error extracting modules.", e);
    }
    return modules;
  }
}
