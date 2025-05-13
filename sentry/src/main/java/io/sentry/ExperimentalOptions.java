package io.sentry;

import io.sentry.protocol.SdkVersion;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Experimental options for new features, these options are going to be promoted to SentryOptions
 * before GA.
 *
 * <p>Beware that experimental options can change at any time.
 */
public final class ExperimentalOptions {
  private @NotNull SentryOptions.Logs logs = new SentryOptions.Logs();

  public ExperimentalOptions(final boolean empty, final @Nullable SdkVersion sdkVersion) {}

  @ApiStatus.Experimental
  public @NotNull SentryOptions.Logs getLogs() {
    return logs;
  }

  @ApiStatus.Experimental
  public void setLogs(@NotNull SentryOptions.Logs logs) {
    this.logs = logs;
  }
}
