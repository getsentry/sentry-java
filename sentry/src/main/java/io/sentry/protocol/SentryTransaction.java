package io.sentry.protocol;

import io.sentry.DateUtils;
import io.sentry.ISpan;
import io.sentry.SentryBaseEvent;
import io.sentry.SentryTracer;
import io.sentry.Span;
import io.sentry.SpanContext;
import io.sentry.SpanStatus;
import io.sentry.util.Objects;
import java.util.ArrayList;
import java.util.Date;
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

  @SuppressWarnings("deprecation")
  public SentryTransaction(final @NotNull SentryTracer sentryTracer) {
    super(sentryTracer.getEventId());
    Objects.requireNonNull(sentryTracer, "sentryTracer is required");
    this.startTimestamp = sentryTracer.getStartTimestamp();
    this.timestamp = DateUtils.getCurrentDateTime();
    this.transaction = sentryTracer.getTag(ISpan.NAME_TAG);
    for (final Span span : sentryTracer.getChildren()) {
      this.spans.add(new SentrySpan(span));
    }
    final Contexts contexts = this.getContexts();
    for (Map.Entry<String, Object> entry : sentryTracer.getContexts().entrySet()) {
      contexts.put(entry.getKey(), entry.getValue());
    }
    contexts.setTrace(sentryTracer.getSpanContext());
    this.setRequest(sentryTracer.getRequest());
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
}
