package io.sentry.metrics;

import io.sentry.HostnameCache;
import io.sentry.IScope;
import io.sentry.ISpan;
import io.sentry.PropagationContext;
import io.sentry.Scopes;
import io.sentry.SentryAttribute;
import io.sentry.SentryAttributeType;
import io.sentry.SentryAttributes;
import io.sentry.SentryDate;
import io.sentry.SentryLevel;
import io.sentry.SentryLogEventAttributeValue;
import io.sentry.SentryMetricsEvent;
import io.sentry.SentryOptions;
import io.sentry.SpanId;
import io.sentry.logger.SentryLogParameters;
import io.sentry.protocol.SdkVersion;
import io.sentry.protocol.SentryId;
import io.sentry.protocol.User;
import io.sentry.util.Platform;
import io.sentry.util.TracingUtils;
import java.util.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MetricsApi implements IMetricsApi {

  private final @NotNull Scopes scopes;

  public MetricsApi(final @NotNull Scopes scopes) {
    this.scopes = scopes;
  }

  @Override
  public void count(final @NotNull String name) {
    captureMetrics(SentryLogParameters.create(null, null), name, "counter", 1.0, null);
  }

  @Override
  public void count(final @NotNull String name, final @Nullable Double value) {
    captureMetrics(SentryLogParameters.create(null, null), name, "counter", value, null);
  }

  @Override
  public void count(final @NotNull String name, final @Nullable String unit) {
    captureMetrics(SentryLogParameters.create(null, null), name, "counter", 1.0, unit);
  }

  @Override
  public void count(
      final @NotNull String name, final @Nullable Double value, final @Nullable String unit) {
    captureMetrics(SentryLogParameters.create(null, null), name, "counter", value, unit);
  }

  @Override
  public void count(
      final @NotNull String name,
      final @Nullable Double value,
      final @Nullable String unit,
      final @NotNull SentryLogParameters params) {
    captureMetrics(params, name, "counter", value, unit);
  }

  @Override
  public void distribution(final @NotNull String name, final @Nullable Double value) {
    captureMetrics(SentryLogParameters.create(null, null), name, "distribution", value, null);
  }

  @Override
  public void distribution(
      final @NotNull String name, final @Nullable Double value, final @Nullable String unit) {
    captureMetrics(SentryLogParameters.create(null, null), name, "distribution", value, unit);
  }

  @Override
  public void distribution(
      final @NotNull String name,
      final @Nullable Double value,
      final @Nullable String unit,
      final @NotNull SentryLogParameters params) {
    captureMetrics(params, name, "distribution", value, unit);
  }

  @Override
  public void gauge(final @NotNull String name, final @Nullable Double value) {
    captureMetrics(SentryLogParameters.create(null, null), name, "gauge", value, null);
  }

  @Override
  public void gauge(
      final @NotNull String name, final @Nullable Double value, final @Nullable String unit) {
    captureMetrics(SentryLogParameters.create(null, null), name, "gauge", value, unit);
  }

  @Override
  public void gauge(
      final @NotNull String name,
      final @Nullable Double value,
      final @Nullable String unit,
      final @NotNull SentryLogParameters params) {
    captureMetrics(params, name, "gauge", value, unit);
  }

  @SuppressWarnings("AnnotateFormatMethod")
  private void captureMetrics(
      final @NotNull SentryLogParameters params,
      final @Nullable String name,
      final @Nullable String type,
      final @Nullable Double value,
      final @Nullable String unit) {
    final @NotNull SentryOptions options = scopes.getOptions();
    try {
      if (!scopes.isEnabled()) {
        options
            .getLogger()
            .log(SentryLevel.WARNING, "Instance is disabled and this 'metrics' call is a no-op.");
        return;
      }

      if (!options.getMetrics().isEnabled()) {
        options
            .getLogger()
            .log(
                SentryLevel.WARNING,
                "Sentry Metrics is disabled and this 'metrics' call is a no-op.");
        return;
      }

      if (name == null) {
        return;
      }

      if (type == null) {
        return;
      }

      if (value == null) {
        return;
      }

      final @Nullable SentryDate timestamp = params.getTimestamp();
      final @NotNull SentryDate timestampToUse =
          timestamp == null ? options.getDateProvider().now() : timestamp;

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
      final SentryMetricsEvent metricsEvent =
          new SentryMetricsEvent(traceId, timestampToUse, name, type, value);
      metricsEvent.setSpanId(spanId);
      metricsEvent.setUnit(unit);
      metricsEvent.setAttributes(createAttributes(params));

      scopes.getClient().captureMetric(metricsEvent, combinedScope);
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.ERROR, "Error while capturing log event", e);
    }
  }

  private @NotNull HashMap<String, SentryLogEventAttributeValue> createAttributes(
      final @NotNull SentryLogParameters params) {
    final @NotNull HashMap<String, SentryLogEventAttributeValue> attributes = new HashMap<>();
    final @NotNull String origin = params.getOrigin();
    if (!"manual".equalsIgnoreCase(origin)) {
      attributes.put(
          "sentry.origin", new SentryLogEventAttributeValue(SentryAttributeType.STRING, origin));
    }

    final @Nullable SentryAttributes incomingAttributes = params.getAttributes();

    if (incomingAttributes != null) {
      for (SentryAttribute attribute : incomingAttributes.getAttributes().values()) {
        final @Nullable Object value = attribute.getValue();
        final @NotNull SentryAttributeType type =
            attribute.getType() == null ? getType(value) : attribute.getType();
        attributes.put(attribute.getName(), new SentryLogEventAttributeValue(type, value));
      }
    }

    final @Nullable SdkVersion sdkVersion = scopes.getOptions().getSdkVersion();
    if (sdkVersion != null) {
      attributes.put(
          "sentry.sdk.name",
          new SentryLogEventAttributeValue(SentryAttributeType.STRING, sdkVersion.getName()));
      attributes.put(
          "sentry.sdk.version",
          new SentryLogEventAttributeValue(SentryAttributeType.STRING, sdkVersion.getVersion()));
    }

    final @Nullable String environment = scopes.getOptions().getEnvironment();
    if (environment != null) {
      attributes.put(
          "sentry.environment",
          new SentryLogEventAttributeValue(SentryAttributeType.STRING, environment));
    }

    final @NotNull SentryId scopeReplayId = scopes.getCombinedScopeView().getReplayId();
    if (!SentryId.EMPTY_ID.equals(scopeReplayId)) {
      attributes.put(
          "sentry.replay_id",
          new SentryLogEventAttributeValue(SentryAttributeType.STRING, scopeReplayId.toString()));
    } else {
      final @NotNull SentryId controllerReplayId =
          scopes.getOptions().getReplayController().getReplayId();
      if (!SentryId.EMPTY_ID.equals(controllerReplayId)) {
        attributes.put(
            "sentry.replay_id",
            new SentryLogEventAttributeValue(
                SentryAttributeType.STRING, controllerReplayId.toString()));
        attributes.put(
            "sentry._internal.replay_is_buffering",
            new SentryLogEventAttributeValue(SentryAttributeType.BOOLEAN, true));
      }
    }

    final @Nullable String release = scopes.getOptions().getRelease();
    if (release != null) {
      attributes.put(
          "sentry.release", new SentryLogEventAttributeValue(SentryAttributeType.STRING, release));
    }

    if (Platform.isJvm()) {
      setServerName(attributes);
    }

    setUser(attributes);

    return attributes;
  }

  private void setServerName(
      final @NotNull HashMap<String, SentryLogEventAttributeValue> attributes) {
    final @NotNull SentryOptions options = scopes.getOptions();
    final @Nullable String optionsServerName = options.getServerName();
    if (optionsServerName != null) {
      attributes.put(
          "server.address",
          new SentryLogEventAttributeValue(SentryAttributeType.STRING, optionsServerName));
    } else if (options.isAttachServerName()) {
      final @Nullable String hostname = HostnameCache.getInstance().getHostname();
      if (hostname != null) {
        attributes.put(
            "server.address",
            new SentryLogEventAttributeValue(SentryAttributeType.STRING, hostname));
      }
    }
  }

  private void setUser(final @NotNull HashMap<String, SentryLogEventAttributeValue> attributes) {
    final @Nullable User user = scopes.getCombinedScopeView().getUser();
    if (user == null) {
      // In case no user is set, we should fallback to the distinct id, known as installation id,
      // which is used on Android as default user id
      final @Nullable String id = scopes.getOptions().getDistinctId();
      if (id != null) {
        attributes.put("user.id", new SentryLogEventAttributeValue(SentryAttributeType.STRING, id));
      }
    } else {
      final @Nullable String id = user.getId();
      if (id != null) {
        attributes.put("user.id", new SentryLogEventAttributeValue(SentryAttributeType.STRING, id));
      }
      final @Nullable String username = user.getUsername();
      if (username != null) {
        attributes.put(
            "user.name", new SentryLogEventAttributeValue(SentryAttributeType.STRING, username));
      }
      final @Nullable String email = user.getEmail();
      if (email != null) {
        attributes.put(
            "user.email", new SentryLogEventAttributeValue(SentryAttributeType.STRING, email));
      }
    }
  }

  private @NotNull SentryAttributeType getType(final @Nullable Object arg) {
    if (arg instanceof Boolean) {
      return SentryAttributeType.BOOLEAN;
    }
    if (arg instanceof Integer) {
      return SentryAttributeType.INTEGER;
    }
    if (arg instanceof Number) {
      return SentryAttributeType.DOUBLE;
    }
    return SentryAttributeType.STRING;
  }
}
