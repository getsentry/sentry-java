package io.sentry.logger;

import io.sentry.HostnameCache;
import io.sentry.IScope;
import io.sentry.ISpan;
import io.sentry.PropagationContext;
import io.sentry.Scopes;
import io.sentry.SentryDate;
import io.sentry.SentryLevel;
import io.sentry.SentryLogEvent;
import io.sentry.SentryLogEventAttributeValue;
import io.sentry.SentryLogLevel;
import io.sentry.SentryOptions;
import io.sentry.SpanId;
import io.sentry.protocol.SdkVersion;
import io.sentry.protocol.SentryId;
import io.sentry.util.Platform;
import io.sentry.util.TracingUtils;
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
    log(SentryLogLevel.TRACE, message, args);
  }

  @Override
  public void debug(final @Nullable String message, final @Nullable Object... args) {
    log(SentryLogLevel.DEBUG, message, args);
  }

  @Override
  public void info(final @Nullable String message, final @Nullable Object... args) {
    log(SentryLogLevel.INFO, message, args);
  }

  @Override
  public void warn(final @Nullable String message, final @Nullable Object... args) {
    log(SentryLogLevel.WARN, message, args);
  }

  @Override
  public void error(final @Nullable String message, final @Nullable Object... args) {
    log(SentryLogLevel.ERROR, message, args);
  }

  @Override
  public void fatal(final @Nullable String message, final @Nullable Object... args) {
    log(SentryLogLevel.FATAL, message, args);
  }

  @Override
  public void log(
      final @NotNull SentryLogLevel level,
      final @Nullable String message,
      final @Nullable Object... args) {
    log(level, null, message, args);
  }

  @Override
  public void log(
      final @NotNull SentryLogLevel level,
      final @Nullable SentryDate timestamp,
      final @Nullable String message,
      final @Nullable Object... args) {
    captureLog(level, timestamp, message, args);
  }

  @SuppressWarnings("AnnotateFormatMethod")
  private void captureLog(
      final @NotNull SentryLogLevel level,
      final @Nullable SentryDate timestamp,
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

      if (!options.getLogs().isEnabled()) {
        options
            .getLogger()
            .log(SentryLevel.WARNING, "Sentry Log is disabled and this 'logger' call is a no-op.");
        return;
      }

      if (message == null) {
        return;
      }

      final @NotNull SentryDate timestampToUse =
          timestamp == null ? options.getDateProvider().now() : timestamp;
      final @NotNull String messageToUse = maybeFormatMessage(message, args);

      final @NotNull IScope combinedScope = scopes.getCombinedScopeView();
      final @NotNull PropagationContext propagationContext = combinedScope.getPropagationContext();
      final @Nullable ISpan span = combinedScope.getSpan();
      if (span == null) {
        TracingUtils.maybeUpdateBaggage(combinedScope, options);
      }
      final @NotNull SentryId traceId =
          span == null ? propagationContext.getTraceId() : span.getSpanContext().getTraceId();
      final @NotNull SpanId spanId =
          span == null ? propagationContext.getSpanId() : span.getSpanContext().getSpanId();
      final SentryLogEvent logEvent =
          new SentryLogEvent(traceId, timestampToUse, messageToUse, level);
      logEvent.setAttributes(createAttributes(message, spanId, args));
      logEvent.setSeverityNumber(level.getSeverityNumber());

      scopes.getClient().captureLog(logEvent, combinedScope);
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.ERROR, "Error while capturing log event", e);
    }
  }

  private @NotNull String maybeFormatMessage(String message, @Nullable Object[] args) {
    if (args == null || args.length == 0) {
      return message;
    }

    try {
      return String.format(message, args);
    } catch (Throwable t) {
      scopes
          .getOptions()
          .getLogger()
          .log(SentryLevel.ERROR, "Error while running log through String.format", t);
      return message;
    }
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

    if (Platform.isJvm()) {
      setServerName(attributes);
    }

    return attributes;
  }

  private void setServerName(
      final @NotNull HashMap<String, SentryLogEventAttributeValue> attributes) {
    final @NotNull SentryOptions options = scopes.getOptions();
    final @Nullable String optionsServerName = options.getServerName();
    if (optionsServerName != null) {
      attributes.put(
          "server.address", new SentryLogEventAttributeValue("string", optionsServerName));
    } else if (options.isAttachServerName()) {
      final @Nullable String hostname = HostnameCache.getInstance().getHostname();
      if (hostname != null) {
        attributes.put("server.address", new SentryLogEventAttributeValue("string", hostname));
      }
    }
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
