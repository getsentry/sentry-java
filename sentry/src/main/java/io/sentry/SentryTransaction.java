package io.sentry;

import io.sentry.protocol.Contexts;
import io.sentry.protocol.SentryId;
import io.sentry.util.Objects;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

@ApiStatus.Internal
public final class SentryTransaction extends SentryBaseEvent implements ITransaction {
  /** The transaction name. */
  private @Nullable String transaction;

  /** The moment in time when span was started. */
  private final @NotNull Date startTimestamp;

  /** The moment in time when span has ended. */
  private @Nullable Date timestamp;

  /** A list of spans within this transaction. Can be empty. */
  private final @NotNull List<Span> spans = new CopyOnWriteArrayList<>();
  /**
   * A hub this transaction is attached to. Marked as transient to be ignored during JSON
   * serialization.
   */
  private @NotNull final transient IHub hub;

  /** The {@code type} property is required in JSON payload sent to Sentry. */
  @SuppressWarnings("UnusedVariable")
  private @NotNull final String type = "transaction";

  /** Creates transaction. */
  SentryTransaction(final @NotNull String name) {
    this(name, new SpanContext(), NoOpHub.getInstance());
  }

  SentryTransaction(final @NotNull TransactionContext transactionContext, final @NotNull IHub hub) {
    this(transactionContext.getName(), transactionContext, hub);
  }

  /**
   * Creates transaction with name and contexts.
   *
   * @param name - transaction name
   * @param contexts - transaction contexts
   * @param hub - the hub
   */
  @TestOnly
  public SentryTransaction(
      final @NotNull String name, final @NotNull SpanContext contexts, final @NotNull IHub hub) {
    Objects.requireNonNull(contexts, "contexts are required");
    this.transaction = Objects.requireNonNull(name, "name is required");
    this.startTimestamp = DateUtils.getCurrentDateTime();
    this.hub = Objects.requireNonNull(hub, "hub is required");
    Contexts ctx = new Contexts();
    ctx.setTrace(contexts);
    this.setContexts(ctx);
  }

  /**
   * Sets transaction name.
   *
   * @param name - transaction name
   */
  @Override
  public void setName(final @NotNull String name) {
    Objects.requireNonNull(name, "name is required");
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
   * Starts a child Span.
   *
   * @param operation - new span operation name
   * @param description - new span description name
   * @return a new transaction span
   */
  @Override
  public @NotNull Span startChild(
      final @NotNull String operation, final @NotNull String description) {
    final Span span = startChild();
    span.setOperation(operation);
    span.setDescription(description);
    return span;
  }

  /**
   * Starts a child Span with given trace id and parent span id.
   *
   * @param parentSpanId - parent span id
   * @return a new transaction span
   */
  @Override
  public @NotNull Span startChild(final @NotNull SpanId parentSpanId) {
    Objects.requireNonNull(parentSpanId, "parentSpanId is required");
    final Span span = new Span(getTraceId(), parentSpanId, this, this.hub);
    this.spans.add(span);
    return span;
  }

  /**
   * Starts a child Span with given trace id and parent span id.
   *
   * @param parentSpanId - parent span id
   * @param operation - span operation name
   * @param description - span description
   * @return a new transaction span
   */
  @Override
  public @NotNull Span startChild(
      final @NotNull SpanId parentSpanId,
      final @NotNull String operation,
      final @NotNull String description) {
    final Span span = startChild(parentSpanId);
    span.setOperation(operation);
    span.setDescription(description);
    return span;
  }

  @Override
  public SentryTraceHeader toSentryTrace() {
    return new SentryTraceHeader(getTraceId(), getSpanId(), isSampled());
  }

  @NotNull
  SpanId getSpanId() {
    return getContexts().getTrace().getSpanId();
  }

  @NotNull
  SentryId getTraceId() {
    return getContexts().getTrace().getTraceId();
  }

  @Override
  public @Nullable Boolean isSampled() {
    return getContexts().getTrace().getSampled();
  }

  @Override
  public void finish() {
    this.timestamp = DateUtils.getCurrentDateTime();
    if (this.throwable != null) {
      hub.setSpanContext(this.throwable, this.getSpanContext());
    }
    this.hub.captureTransaction(this, null);
  }

  /**
   * Sets transaction operation.
   *
   * @param op - operation
   */
  @Override
  public void setOperation(@Nullable String op) {
    this.getContexts().getTrace().setOperation(op);
  }

  /**
   * Sets transaction description.
   *
   * @param description - the description.
   */
  @Override
  public void setDescription(@Nullable String description) {
    this.getContexts().getTrace().setDescription(description);
  }

  @Override
  public @Nullable String getDescription() {
    return this.getContexts().getTrace().getDescription();
  }

  @Override
  public @NotNull SpanContext getSpanContext() {
    return this.getContexts().getTrace();
  }

  /**
   * Sets transaction status.
   *
   * @param spanStatus - the status
   */
  @Override
  public void setStatus(@Nullable SpanStatus spanStatus) {
    this.getContexts().getTrace().setStatus(spanStatus);
  }

  /**
   * Returns the transaction name.
   *
   * @return transaction name.
   */
  @Override
  @ApiStatus.Internal
  public @Nullable String getTransaction() {
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

  @Override
  @TestOnly
  public @NotNull List<Span> getSpans() {
    return spans;
  }

  /** @return the latest span that is not finished or null if not found. */
  @Override
  public @Nullable Span getLatestActiveSpan() {
    final List<Span> spans = new ArrayList<>(this.spans);
    if (!spans.isEmpty()) {
      for (int i = spans.size() - 1; i >= 0; i--) {
        if (!spans.get(i).isFinished()) {
          return spans.get(i);
        }
      }
    }
    return null;
  }
}
