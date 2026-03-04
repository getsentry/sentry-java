package io.sentry.android.core;

import io.sentry.ISentryClient;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.metrics.MetricsBatchProcessor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class AndroidMetricsBatchProcessor extends MetricsBatchProcessor
    implements AppState.AppStateListener {

  public AndroidMetricsBatchProcessor(
      final @NotNull SentryOptions options, final @NotNull ISentryClient client) {
    super(options, client);
    AppState.getInstance().addAppStateListener(this);
  }

  @Override
  public void onForeground() {
    // no-op
  }

  @Override
  public void onBackground() {
    try {
      options
          .getExecutorService()
          .submit(
              new Runnable() {
                @Override
                public void run() {
                  flush(MetricsBatchProcessor.FLUSH_AFTER_MS);
                }
              });
    } catch (Throwable t) {
      options
          .getLogger()
          .log(SentryLevel.ERROR, t, "Failed to submit metrics flush in onBackground()");
    }
  }

  @Override
  public void close(boolean isRestarting) {
    AppState.getInstance().removeAppStateListener(this);
    super.close(isRestarting);
  }
}
