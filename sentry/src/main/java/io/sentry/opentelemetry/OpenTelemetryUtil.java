package io.sentry.opentelemetry;

import io.sentry.NoOpLogger;
import io.sentry.SentryOpenTelemetryMode;
import io.sentry.SentryOptions;
import io.sentry.util.LoadClass;
import io.sentry.util.Platform;
import io.sentry.util.SpanUtils;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class OpenTelemetryUtil {

  @ApiStatus.Internal
  public static void applyIgnoredSpanOrigins(
      final @NotNull SentryOptions options, final @NotNull LoadClass loadClass) {
    if (Platform.isJvm()) {
      final @NotNull List<String> ignored = ignoredSpanOrigins(options, loadClass);
      for (String origin : ignored) {
        options.addIgnoredSpanOrigin(origin);
      }
    }
  }

  private static @NotNull List<String> ignoredSpanOrigins(
      final @NotNull SentryOptions options, final @NotNull LoadClass loadClass) {
    final @NotNull SentryOpenTelemetryMode openTelemetryMode = options.getOpenTelemetryMode();
    if (SentryOpenTelemetryMode.AUTO.equals(openTelemetryMode)) {
      if (loadClass.isClassAvailable(
          "io.sentry.opentelemetry.agent.AgentMarker", NoOpLogger.getInstance())) {
        return SpanUtils.ignoredSpanOriginsForOpenTelemetry(SentryOpenTelemetryMode.AGENT);
      }
      if (loadClass.isClassAvailable(
          "io.sentry.opentelemetry.agent.AgentlessMarker", NoOpLogger.getInstance())) {
        return SpanUtils.ignoredSpanOriginsForOpenTelemetry(SentryOpenTelemetryMode.AGENTLESS);
      }
      if (loadClass.isClassAvailable(
          "io.sentry.opentelemetry.agent.AgentlessSpringMarker", NoOpLogger.getInstance())) {
        return SpanUtils.ignoredSpanOriginsForOpenTelemetry(
            SentryOpenTelemetryMode.AGENTLESS_SPRING);
      }
    } else {
      return SpanUtils.ignoredSpanOriginsForOpenTelemetry(openTelemetryMode);
    }

    return Collections.emptyList();
  }
}
