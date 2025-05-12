package io.sentry.logger;

import io.sentry.DataCategory;
import io.sentry.Hint;
import io.sentry.ISpan;
import io.sentry.PropagationContext;
import io.sentry.Scopes;
import io.sentry.SentryDate;
import io.sentry.SentryLevel;
import io.sentry.SentryLogEvent;
import io.sentry.SentryLogEventAttributeValue;
import io.sentry.SentryOptions;
import io.sentry.SpanId;
import io.sentry.clientreport.DiscardReason;
import io.sentry.protocol.SdkVersion;
import io.sentry.protocol.SentryId;
import io.sentry.util.Random;
import io.sentry.util.SentryRandom;
import java.util.HashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
public final class LoggerApi implements ILoggerApi {

  private final @NotNull Scopes scopes;

  public LoggerApi(final @NotNull Scopes scopes) {
    this.scopes = scopes;
  }

  @Override
  public void trace(final @Nullable String message, final @Nullable Object... args) {
    // TODO SentryLevel.TRACE does not exists yet so we just report it as DEBUG for now
    log(SentryLevel.DEBUG, message, args);
  }

  @Override
  public void debug(final @Nullable String message, final @Nullable Object... args) {
    log(SentryLevel.DEBUG, message, args);
  }

  @Override
  public void info(final @Nullable String message, final @Nullable Object... args) {
    log(SentryLevel.INFO, message, args);
  }

  @Override
  public void warn(final @Nullable String message, final @Nullable Object... args) {
    log(SentryLevel.WARNING, message, args);
  }

  @Override
  public void error(final @Nullable String message, final @Nullable Object... args) {
    log(SentryLevel.ERROR, message, args);
  }

  @Override
  public void fatal(final @Nullable String message, final @Nullable Object... args) {
    log(SentryLevel.FATAL, message, args);
  }

  @Override
  public void log(
      final @NotNull SentryLevel level,
      final @Nullable String message,
      final @Nullable Object... args) {
    log(level, null, message, null, args);
  }

  @Override
  public void log(
      final @NotNull SentryLevel level,
      final @Nullable SentryDate timestamp,
      final @Nullable String message,
      final @Nullable Hint hint,
      final @Nullable Object... args) {
    captureLog(level, timestamp, hint, message, args);
  }

  @SuppressWarnings("AnnotateFormatMethod")
  private void captureLog(
      final @NotNull SentryLevel level,
      final @Nullable SentryDate timestamp,
      final @Nullable Hint hint,
      final @Nullable String message,
      final @Nullable Object... args) {
    final @NotNull SentryOptions options = scopes.getOptions();
    try {
      if (!scopes.isEnabled()) {
        options
            .getLogger()
            .log(SentryLevel.WARNING, "Instance is disabled and this 'logger' call is a no-op.");
        return;
      }

      if (!options.getExperimental().getLogs().isEnabled()) {
        options
            .getLogger()
            .log(SentryLevel.WARNING, "Sentry Log is disabled and this 'logger' call is a no-op.");
        return;
      }

      if (message == null) {
        return;
      }

      if (!sampleLog(options)) {
        options
            .getLogger()
            .log(SentryLevel.DEBUG, "Log Event was dropped due to sampling decision.");
        options
            .getClientReportRecorder()
            .recordLostEvent(DiscardReason.SAMPLE_RATE, DataCategory.LogItem);
        return;
      }

      final @NotNull SentryDate timestampToUse =
          timestamp == null ? options.getDateProvider().now() : timestamp;
      final @NotNull String messageToUse = args == null ? message : String.format(message, args);
      final @NotNull PropagationContext propagationContext =
          scopes.getCombinedScopeView().getPropagationContext();
      final @Nullable ISpan span = scopes.getCombinedScopeView().getSpan();
      final @NotNull SentryId traceId =
          span == null ? propagationContext.getTraceId() : span.getSpanContext().getTraceId();
      final @NotNull SpanId spanId =
          span == null ? propagationContext.getSpanId() : span.getSpanContext().getSpanId();
      final SentryLogEvent logEvent =
          new SentryLogEvent(traceId, timestampToUse, messageToUse, level);
      logEvent.setAttributes(createAttributes(message, spanId, args));

      scopes.getClient().captureLog(logEvent, scopes.getCombinedScopeView(), hint);
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.ERROR, "Error while capturing log event", e);
    }
  }

  private boolean sampleLog(final @NotNull SentryOptions options) {
    final @Nullable Random random =
        options.getExperimental().getLogs().getSampleRate() == null ? null : SentryRandom.current();
    if (options.getExperimental().getLogs().getSampleRate() != null && random != null) {
      final double sampling = options.getExperimental().getLogs().getSampleRate();
      return !(sampling < random.nextDouble()); // bad luck
    }
    return false;
  }

  private @NotNull HashMap<String, SentryLogEventAttributeValue> createAttributes(
      final @NotNull String message, final @NotNull SpanId spanId, final @Nullable Object... args) {
    final @NotNull HashMap<String, SentryLogEventAttributeValue> attributes = new HashMap<>();
    if (args != null) {
      int i = 0;
      for (Object arg : args) {
        final @NotNull String type = getType(arg);
        attributes.put(
            "sentry.message.parameter." + i, new SentryLogEventAttributeValue(type, arg));
        i++;
      }
      if (i > 0) {
        attributes.put(
            "sentry.message.template", new SentryLogEventAttributeValue("string", message));
      }
    }

    final @Nullable SdkVersion sdkVersion = scopes.getOptions().getSdkVersion();
    if (sdkVersion != null) {
      attributes.put(
          "sentry.sdk.name", new SentryLogEventAttributeValue("string", sdkVersion.getName()));
      attributes.put(
          "sentry.sdk.version",
          new SentryLogEventAttributeValue("string", sdkVersion.getVersion()));
    }

    final @Nullable String environment = scopes.getOptions().getEnvironment();
    if (environment != null) {
      attributes.put("sentry.environment", new SentryLogEventAttributeValue("string", environment));
    }
    final @Nullable String release = scopes.getOptions().getRelease();
    if (release != null) {
      attributes.put("sentry.release", new SentryLogEventAttributeValue("string", release));
    }

    attributes.put(
        "sentry.trace.parent_span_id", new SentryLogEventAttributeValue("string", spanId));
    return attributes;
  }

  private @NotNull String getType(final @Nullable Object arg) {
    if (arg instanceof Boolean) {
      return "boolean";
    }
    if (arg instanceof Integer) {
      return "integer";
    }
    if (arg instanceof Number) {
      return "double";
    }
    return "string";
  }
}
