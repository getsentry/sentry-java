package io.sentry;

import io.sentry.protocol.SentryId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Transaction extends SentryBaseEvent<TransactionContexts> implements ISpan {
  /** The transaction name. */
  private @Nullable String transaction;

  /** The moment in time when span was started. */
  private final @NotNull Date startTimestamp;

  /** The moment in time when span has ended. */
  private @Nullable Date timestamp;

  /** A list of spans within this transaction. Can be empty. */
  private final @NotNull List<Span> spans = new ArrayList<>();
  /**
   * A hub this transaction is attached to. Marked as transient to be ignored during JSON
   * serialization.
   */
  private @NotNull final transient IHub hub;

  /** The {@code type} property is required in JSON payload sent to Sentry. */
  @SuppressWarnings("UnusedVariable")
  private @NotNull final String type = "transaction";

  /** Creates unnamed transaction. */
  Transaction(final @NotNull String name) {
    this(name, new TransactionContexts(), NoOpHub.getInstance());
  }

  /**
   * Creates transaction with name and contexts.
   *
   * @param name - transaction name
   * @param contexts - transaction contexts
   */
  Transaction(
      final @Nullable String name,
      final @NotNull TransactionContexts contexts,
      final @NotNull IHub hub) {
    this.transaction = name;
    this.startTimestamp = DateUtils.getCurrentDateTime();
    this.hub = hub;
    this.setContexts(contexts);
  }

  /**
   * Sets transaction name.
   *
   * @param name - transaction name
   */
  void setName(final @NotNull String name) {
    this.transaction = name;
  }

  /**
   * Starts a child Span.
   *
   * @return a new transaction span
   */
  @Override
  public Span startChild() {
    return this.startChild(getSpanId());
  }

  /**
   * Starts a child Span with given trace id and parent span id.
   *
   * @param parentSpanId - parent span id
   * @return a new transaction span
   */
  Span startChild(final @NotNull SpanId parentSpanId) {
    final Span span = new Span(getTraceId(), parentSpanId, this);
    this.spans.add(span);
    return span;
  }

  @Override
  public String toSentryTrace() {
    return this.getContexts().getTraceContext().toSentryTrace();
  }

  @NotNull
  SpanId getSpanId() {
    return getContexts().getTraceContext().getSpanId();
  }

  @NotNull
  SentryId getTraceId() {
    return getContexts().getTraceContext().getTraceId();
  }

  @Override
  public void finish() {
    this.timestamp = DateUtils.getCurrentDateTime();
    this.hub.captureTransaction(this, null);
  }

  /**
   * Returns the transaction name.
   *
   * @return transaction name.
   */
  @Nullable
  String getTransaction() {
    return transaction;
  }

  @NotNull
  Date getStartTimestamp() {
    return startTimestamp;
  }

  @Nullable
  Date getTimestamp() {
    return timestamp;
  }

  @NotNull
  Collection<Span> getSpans() {
    return spans;
  }

  /** @return the latest span that is not finished or null if not found. */
  @Nullable
  Span getLatestActiveSpan() {
    final List<Span> spans = new ArrayList<>(this.spans);
    for (int i = spans.size() - 1; i >= 0; i--) {
      if (!spans.get(i).isFinished()) {
        return spans.get(i);
      }
    }
    return null;
  }
}
