package io.sentry;

import io.sentry.protocol.SentryId;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Transaction extends SentryBaseEvent<TransactionContexts>
    implements ISpan, Cloneable {
  /** The transaction name. */
  private @Nullable String transaction;

  @SuppressWarnings("UnusedVariable")
  private @NotNull final String platform = "java";

  /** The moment in time when span was started. */
  private @NotNull Date startTimestamp;

  /** The moment in time when span has ended. */
  private @Nullable Date timestamp;

  /** A list of spans within this transaction. Can be empty. */
  private final List<Span> spans = new CopyOnWriteArrayList<>();

  /**
   * A hub this transaction is attached to. Marked as transient to be ignored during JSON
   * serialization.
   */
  private @NotNull final transient IHub hub;

  /** The {@code type} property is required in JSON payload sent to Sentry. */
  @SuppressWarnings("UnusedVariable")
  private @NotNull final String type = "transaction";

  /** The {@code platform} property is required in JSON payload sent to Sentry. */

  /** Creates unnamed transaction. */
  Transaction() {
    this(null, new TransactionContexts(new Trace()), NoOpHub.getInstance());
  }

  /**
   * Creates unnamed transaction with context attached to a hub.
   *
   * @param contexts - transaction context
   * @param hub - the hub transaction is attached to
   */
  public Transaction(final @NotNull TransactionContexts contexts, final @NotNull IHub hub) {
    this(null, contexts, hub);
  }

  /**
   * Creates transaction with name and contexts.
   *
   * @param name - transaction name
   * @param contexts - transaction contexts
   */
  public Transaction(
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
  public void setName(final @Nullable String name) {
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
  public String toTraceparent() {
    return String.format("%s-%s", getTraceId(), getSpanId());
  }

  @NotNull SpanId getSpanId() {
    return getContexts().getTrace().getSpanId();
  }

  @NotNull SentryId getTraceId() {
    return getContexts().getTrace().getTraceId();
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
  public @Nullable String getTransaction() {
    return transaction;
  }

  public @NotNull Date getStartTimestamp() {
    return startTimestamp;
  }

  public @Nullable Date getTimestamp() {
    return timestamp;
  }

  @NotNull List<Span> getSpans() {
    return spans;
  }

  @Override
  protected Transaction clone() throws CloneNotSupportedException {
    final Transaction clone = (Transaction) super.clone();
    clone.setContexts(this.getContexts().clone());
    return clone;
  }
}
