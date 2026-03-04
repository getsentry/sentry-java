package io.sentry.android.core;

import io.sentry.ISentryClient;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.logger.LoggerBatchProcessor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class AndroidLoggerBatchProcessor extends LoggerBatchProcessor
    implements AppState.AppStateListener {

  public AndroidLoggerBatchProcessor(
      @NotNull SentryOptions options, @NotNull ISentryClient client) {
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
                  flush(LoggerBatchProcessor.FLUSH_AFTER_MS);
                }
              });
    } catch (Throwable t) {
      options.getLogger().log(SentryLevel.ERROR, t, "Failed to submit log flush in onBackground()");
    }
  }

  @Override
  public void close(boolean isRestarting) {
    AppState.getInstance().removeAppStateListener(this);
    super.close(isRestarting);
  }
}
