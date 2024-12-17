package io.sentry.opentelemetry;

import io.sentry.SentryOpenTelemetryMode;
import io.sentry.SentryOptions;
import io.sentry.util.SpanUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
public final class OpenTelemetryUtil {

  public static void applyOpenTelemetryOptions(
      final @Nullable SentryOptions options, final @NotNull SentryOpenTelemetryMode mode) {
    if (options != null) {
      options.setIgnoredSpanOrigins(SpanUtils.ignoredSpanOriginsForOpenTelemetry(mode));
    }
  }
}
