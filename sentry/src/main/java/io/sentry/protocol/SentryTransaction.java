package io.sentry.protocol;

import io.sentry.SentryBaseEvent;
import io.sentry.SentryTracer;
import io.sentry.Span;
import io.sentry.SpanContext;
import io.sentry.SpanStatus;
import io.sentry.util.Objects;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SentryTransaction extends SentryBaseEvent {
  /** The transaction name. */
  @SuppressWarnings("UnusedVariable")
  private @Nullable String transaction;

  /** The moment in time when span was started. */
  private final @NotNull Date startTimestamp;

  /** The moment in time when span has ended. */
  private @Nullable Date timestamp;

  /** A list of spans within this transaction. Can be empty. */
  private final @NotNull List<SentrySpan> spans = new ArrayList<>();

  /** The {@code type} property is required in JSON payload sent to Sentry. */
  @SuppressWarnings("UnusedVariable")
  private @NotNull final String type = "transaction";

  private @NotNull final Map<String, @NotNull MeasurementValue> measurements = new HashMap<>();

  @SuppressWarnings("deprecation")
  public SentryTransaction(final @NotNull SentryTracer sentryTracer) {
    super(sentryTracer.getEventId());
    Objects.requireNonNull(sentryTracer, "sentryTracer is required");
    this.startTimestamp = sentryTracer.getStartTimestamp();
    this.timestamp = sentryTracer.getTimestamp();
    this.transaction = sentryTracer.getName();
    for (final Span span : sentryTracer.getChildren()) {
      if (Boolean.TRUE.equals(span.isSampled())) {
        this.spans.add(new SentrySpan(span));
      }
    }
    final Contexts contexts = this.getContexts();
    for (final Map.Entry<String, Object> entry : sentryTracer.getContexts().entrySet()) {
      contexts.put(entry.getKey(), entry.getValue());
    }
    this.setRequest(sentryTracer.getRequest());
    final SpanContext tracerContext = sentryTracer.getSpanContext();
    // tags must be placed on the root of the transaction instead of contexts.trace.tags
    contexts.setTrace(
        new SpanContext(
            tracerContext.getTraceId(),
            tracerContext.getSpanId(),
            tracerContext.getParentSpanId(),
            tracerContext.getOperation(),
            tracerContext.getDescription(),
            tracerContext.getSampled(),
            tracerContext.getStatus()));
    for (final Map.Entry<String, String> tag : tracerContext.getTags().entrySet()) {
      this.setTag(tag.getKey(), tag.getValue());
    }

    final Map<String, Object> data = sentryTracer.getData();
    if (data != null) {
      for (final Map.Entry<String, Object> tag : data.entrySet()) {
        this.setExtra(tag.getKey(), tag.getValue());
      }
    }
  }

  public @NotNull List<SentrySpan> getSpans() {
    return spans;
  }

  public boolean isFinished() {
    return this.timestamp != null;
  }

  public @Nullable String getTransaction() {
    return transaction;
  }

  public @NotNull Date getStartTimestamp() {
    return startTimestamp;
  }

  public @Nullable Date getTimestamp() {
    return timestamp;
  }

  public @NotNull String getType() {
    return type;
  }

  public @Nullable SpanStatus getStatus() {
    final SpanContext trace = this.getContexts().getTrace();
    return trace != null ? trace.getStatus() : null;
  }

  public boolean isSampled() {
    final SpanContext trace = this.getContexts().getTrace();
    return trace != null && Boolean.TRUE.equals(trace.getSampled());
  }

  public @NotNull Map<String, @NotNull MeasurementValue> getMeasurements() {
    return measurements;
  }
}
