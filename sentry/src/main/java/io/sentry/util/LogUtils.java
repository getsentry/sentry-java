package io.sentry.util;

import io.sentry.ILogger;
import io.sentry.SentryLevel;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class LogUtils {

  public static void logIfNotFlushable(final @NotNull ILogger logger, final @Nullable Object hint) {
    logger.log(
        SentryLevel.DEBUG,
        "%s is not Flushable",
        hint != null ? hint.getClass().getCanonicalName() : "Hint");
  }

  public static void logIfNotRetryable(final @NotNull ILogger logger, final @Nullable Object hint) {
    logger.log(
        SentryLevel.DEBUG,
        "%s is not Retryable",
        hint != null ? hint.getClass().getCanonicalName() : "Hint");
  }
}
