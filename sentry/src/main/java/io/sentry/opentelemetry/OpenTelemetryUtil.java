package io.sentry.opentelemetry;

import io.sentry.*;
import io.sentry.util.LoadClass;
import io.sentry.util.Platform;
import io.sentry.util.SpanUtils;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class OpenTelemetryUtil {

  @ApiStatus.Internal
  public static void applyIgnoredSpanOrigins(final @NotNull SentryOptions options) {
    if (Platform.isJvm()) {
      final @NotNull List<String> ignored = ignoredSpanOrigins(options);
      for (String origin : ignored) {
        options.addIgnoredSpanOrigin(origin);
      }
    }
  }

  @ApiStatus.Internal
  public static void updateOpenTelemetryModeIfAuto(
      final @NotNull SentryOptions options, final @NotNull LoadClass loadClass) {
    if (!Platform.isJvm()) {
      return;
    }

    final @NotNull SentryOpenTelemetryMode openTelemetryMode = options.getOpenTelemetryMode();
    if (SentryOpenTelemetryMode.AUTO.equals(openTelemetryMode)) {
      if (loadClass.isClassAvailable(
          "io.sentry.opentelemetry.agent.AgentMarker", NoOpLogger.getInstance())) {
        options
            .getLogger()
            .log(SentryLevel.DEBUG, "openTelemetryMode has been inferred from AUTO to AGENT");
        options.setOpenTelemetryMode(SentryOpenTelemetryMode.AGENT);
        return;
      }
      if (loadClass.isClassAvailable(
          "io.sentry.opentelemetry.agent.AgentlessMarker", NoOpLogger.getInstance())) {
        options
            .getLogger()
            .log(SentryLevel.DEBUG, "openTelemetryMode has been inferred from AUTO to AGENTLESS");
        options.setOpenTelemetryMode(SentryOpenTelemetryMode.AGENTLESS);
        return;
      }
      if (loadClass.isClassAvailable(
          "io.sentry.opentelemetry.agent.AgentlessSpringMarker", NoOpLogger.getInstance())) {
        options
            .getLogger()
            .log(
                SentryLevel.DEBUG,
                "openTelemetryMode has been inferred from AUTO to AGENTLESS_SPRING");
        options.setOpenTelemetryMode(SentryOpenTelemetryMode.AGENTLESS_SPRING);
        return;
      }
    }
  }

  private static @NotNull List<String> ignoredSpanOrigins(final @NotNull SentryOptions options) {
    final @NotNull SentryOpenTelemetryMode openTelemetryMode = options.getOpenTelemetryMode();

    if (SentryOpenTelemetryMode.OFF.equals(openTelemetryMode)) {
      return Collections.emptyList();
    }

    return SpanUtils.ignoredSpanOriginsForOpenTelemetry(openTelemetryMode);
  }
}
