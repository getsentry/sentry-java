package io.sentry.util;

import io.sentry.ILogger;
import io.sentry.SentryLevel;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class LogUtils {

  public static void logNotInstanceOf(
      final @NotNull Class<?> expectedClass,
      final @Nullable Object sentrySdkHint,
      final @NotNull ILogger logger) {
    logger.log(
        SentryLevel.DEBUG,
        "%s is not %s",
        sentrySdkHint != null ? sentrySdkHint.getClass().getCanonicalName() : "Hint",
        expectedClass.getCanonicalName());
  }

  //  public static void logIfNotFlushable(
  //      final @NotNull ILogger logger, final @Nullable Object sentrySdkHint) {
  //    logger.log(
  //        SentryLevel.DEBUG,
  //        "%s is not Flushable",
  //        sentrySdkHint != null ? sentrySdkHint.getClass().getCanonicalName() : "Hint");
  //  }
  //
  //  public static void logIfNotRetryable(
  //      final @NotNull ILogger logger, final @Nullable Object sentrySdkHint) {
  //    logger.log(
  //        SentryLevel.DEBUG,
  //        "%s is not Retryable",
  //        sentrySdkHint != null ? sentrySdkHint.getClass().getCanonicalName() : "Hint");
  //  }
}
