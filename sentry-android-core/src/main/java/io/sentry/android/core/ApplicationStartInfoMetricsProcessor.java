package io.sentry.android.core;

import android.app.ApplicationStartInfo;
import androidx.annotation.RequiresApi;
import io.sentry.IScopes;
import io.sentry.metrics.SentryMetricsParameters;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Processor that emits counter metrics from ApplicationStartInfo data.
 *
 * <p>This processor emits an app.launch counter metric with attributes for start reason, start
 * type, and launch mode.
 *
 * <p>Requires API level 35 (Android 15) or higher.
 */
@RequiresApi(api = 35)
final class ApplicationStartInfoMetricsProcessor implements IApplicationStartInfoProcessor {

  private final @NotNull SentryAndroidOptions options;

  ApplicationStartInfoMetricsProcessor(final @NotNull SentryAndroidOptions options) {
    this.options = options;
  }

  @Override
  public void process(
      final @NotNull ApplicationStartInfo startInfo,
      final @NotNull Map<String, String> tags,
      final @NotNull IScopes scopes) {

    @SuppressWarnings("unchecked")
    final Map<String, Object> attributes = (Map<String, Object>) (Map<?, ?>) tags;
    scopes.metrics().count("app.launch", 1.0, null, SentryMetricsParameters.create(attributes));
  }
}
