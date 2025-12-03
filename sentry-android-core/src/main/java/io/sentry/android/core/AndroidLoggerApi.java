package io.sentry.android.core;

import io.sentry.Scopes;
import io.sentry.SentryLevel;
import io.sentry.logger.LoggerApi;
import io.sentry.logger.LoggerBatchProcessor;
import java.io.Closeable;
import java.io.IOException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class AndroidLoggerApi extends LoggerApi implements Closeable, AppState.AppStateListener {

  public AndroidLoggerApi(final @NotNull Scopes scopes) {
    super(scopes);
    AppState.getInstance().addAppStateListener(this);
  }

  @Override
  @ApiStatus.Internal
  public void close() throws IOException {
    AppState.getInstance().removeAppStateListener(this);
  }

  @Override
  public void onForeground() {}

  @Override
  public void onBackground() {
    try {
      scopes
          .getOptions()
          .getExecutorService()
          .submit(
              new Runnable() {
                @Override
                public void run() {
                  scopes.getClient().flushLogs(LoggerBatchProcessor.FLUSH_AFTER_MS);
                }
              });
    } catch (Throwable t) {
      scopes
          .getOptions()
          .getLogger()
          .log(SentryLevel.ERROR, t, "Failed to submit log flush runnable");
    }
  }
}
