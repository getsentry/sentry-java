package io.sentry.core.util;

import io.sentry.core.ILogger;
import io.sentry.core.SentryLevel;
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
