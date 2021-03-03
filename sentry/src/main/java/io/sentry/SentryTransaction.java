package io.sentry;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
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
  private final @NotNull List<Span> spans = new CopyOnWriteArrayList<>();

  /** The {@code type} property is required in JSON payload sent to Sentry. */
  @SuppressWarnings("UnusedVariable")
  private @NotNull final String type = "transaction";

  public SentryTransaction(SentryTracer sentryTracer) {
    this.spans.addAll(sentryTracer.getChildren());
    this.startTimestamp = sentryTracer.getStartTimestamp();
    this.timestamp = DateUtils.getCurrentDateTime();
    this.transaction = sentryTracer.getTag(ISpan.NAME_TAG);
    this.getContexts().setTrace(sentryTracer.getSpanContext());
  }

  public @NotNull List<Span> getSpans() {
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

  public boolean isSampled() {
    final SpanContext trace = this.getContexts().getTrace();
    return trace != null && Objects.equals(trace.getSampled(), Boolean.TRUE);
  }
}
