package io.sentry;

import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
final class SentryTransaction extends SentryBaseEvent {
  /** The transaction name. */
  @SuppressWarnings("UnusedVariable")
  private @NotNull String transaction;

  /** The moment in time when span was started. */
  @SuppressWarnings("UnusedVariable")
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
    this.getContexts().setTrace(sentryTracer.getSpanContext());
    this.transaction = sentryTracer.getRoot().getTag("sentry-name");
  }

  public List<Span> getSpans() {
    return spans;
  }

  public boolean isFinished() {
    return this.timestamp != null;
  }
}
