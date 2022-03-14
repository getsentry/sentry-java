package io.sentry;

import io.sentry.protocol.SentryId;
import io.sentry.protocol.SentryTransaction;
import io.sentry.protocol.User;
import io.sentry.util.Objects;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SentryTracer implements ITransaction {
  private final @NotNull SentryId eventId = new SentryId();
  private final @NotNull Span root;
  private final @NotNull List<Span> children = new CopyOnWriteArrayList<>();
  private final @NotNull IHub hub;
  private @NotNull String name;
  /**
   * When `waitForChildren` is set to `true`, tracer will finish only when both conditions are met
   * (the order of meeting condition does not matter): - tracer itself is finished - all child spans
   * are finished
   */
  private final boolean waitForChildren;

  /**
   * Holds the status for finished tracer. Tracer can have finishedStatus set, but not be finished
   * itself when `waitForChildren` is set to `true`, `#finish()` method was called but there are
   * unfinished children spans.
   */
  private @NotNull FinishStatus finishStatus = FinishStatus.NOT_FINISHED;

  /**
   * When `waitForChildren` is set to `true` and this callback is set, it's called before the
   * transaction is captured.
   */
  private final @Nullable TransactionFinishedCallback transactionFinishedCallback;

  private @Nullable TraceState traceState;

  public SentryTracer(final @NotNull TransactionContext context, final @NotNull IHub hub) {
    this(context, hub, null);
  }

  public SentryTracer(
      final @NotNull TransactionContext context,
      final @NotNull IHub hub,
      final boolean waitForChildren,
      final @Nullable TransactionFinishedCallback transactionFinishedCallback) {
    this(context, hub, null, waitForChildren, transactionFinishedCallback);
  }

  SentryTracer(
      final @NotNull TransactionContext context,
      final @NotNull IHub hub,
      final @Nullable Date startTimestamp) {
    this(context, hub, startTimestamp, false, null);
  }

  SentryTracer(
      final @NotNull TransactionContext context,
      final @NotNull IHub hub,
      final @Nullable Date startTimestamp,
      final boolean waitForChildren,
      final @Nullable TransactionFinishedCallback transactionFinishedCallback) {
    Objects.requireNonNull(context, "context is required");
    Objects.requireNonNull(hub, "hub is required");
    this.root = new Span(context, this, hub, startTimestamp);
    this.name = context.getName();
    this.hub = hub;
    this.waitForChildren = waitForChildren;
    this.transactionFinishedCallback = transactionFinishedCallback;
  }

  public @NotNull List<Span> getChildren() {
    return children;
  }

  public @NotNull Date getStartTimestamp() {
    return this.root.getStartTimestamp();
  }

  public @Nullable Double getTimestamp() {
    return this.root.getTimestamp();
  }

  /**
   * Starts a child Span with given trace id and parent span id.
   *
   * @param parentSpanId - parent span id
   * @param operation - span operation name
   * @param description - span description
   * @return a new transaction span
   */
  @NotNull
  ISpan startChild(
      final @NotNull SpanId parentSpanId,
      final @NotNull String operation,
      final @Nullable String description) {
    final ISpan span = createChild(parentSpanId, operation);
    span.setDescription(description);
    return span;
  }

  @NotNull
  ISpan startChild(
      final @NotNull SpanId parentSpanId,
      final @NotNull String operation,
      final @Nullable String description,
      final @Nullable Date timestamp) {
    return createChild(parentSpanId, operation, description, timestamp);
  }

  /**
   * Starts a child Span with given trace id and parent span id.
   *
   * @param parentSpanId - parent span id
   * @return a new transaction span
   */
  @NotNull
  private ISpan createChild(final @NotNull SpanId parentSpanId, final @NotNull String operation) {
    return createChild(parentSpanId, operation, null, null);
  }

  @NotNull
  private ISpan createChild(
      final @NotNull SpanId parentSpanId,
      final @NotNull String operation,
      final @Nullable String description,
      @Nullable Date timestamp) {
    Objects.requireNonNull(parentSpanId, "parentSpanId is required");
    Objects.requireNonNull(operation, "operation is required");
    final Span span =
        new Span(
            root.getTraceId(),
            parentSpanId,
            this,
            operation,
            this.hub,
            timestamp,
            __ -> {
              final FinishStatus finishStatus = this.finishStatus;
              if (finishStatus.isFinishing) {
                finish(finishStatus.spanStatus);
              }
            });
    span.setDescription(description);
    this.children.add(span);
    return span;
  }

  @Override
  public @NotNull ISpan startChild(final @NotNull String operation) {
    return this.startChild(operation, (String) null);
  }

  @Override
  public @NotNull ISpan startChild(
      final @NotNull String operation, @Nullable String description, @Nullable Date timestamp) {
    return createChild(operation, description, timestamp);
  }

  @Override
  public @NotNull ISpan startChild(
      final @NotNull String operation, final @Nullable String description) {
    return createChild(operation, description, null);
  }

  private @NotNull ISpan createChild(
      final @NotNull String operation,
      final @Nullable String description,
      @Nullable Date timestamp) {
    if (children.size() < hub.getOptions().getMaxSpans()) {
      return root.startChild(operation, description, timestamp);
    } else {
      hub.getOptions()
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Span operation: %s, description: %s dropped due to limit reached. Returning NoOpSpan.",
              operation,
              description);
      return NoOpSpan.getInstance();
    }
  }

  @Override
  public @NotNull SentryTraceHeader toSentryTrace() {
    return root.toSentryTrace();
  }

  @Override
  public void finish() {
    this.finish(this.getStatus());
  }

  @Override
  public void finish(@Nullable SpanStatus status) {
    this.finishStatus = FinishStatus.finishing(status);
    if (!root.isFinished() && (!waitForChildren || hasAllChildrenFinished())) {
      ProfilingTraceData profilingTraceData = null;
      if (hub.getOptions().isProfilingEnabled()) {
        profilingTraceData = hub.getOptions().getTransactionProfiler().onTransactionFinish(this);
      }
      root.finish(finishStatus.spanStatus);

      // finish unfinished children
      Double finishTimestamp = root.getHighPrecisionTimestamp();
      if (finishTimestamp == null) {
        hub.getOptions()
            .getLogger()
            .log(
                SentryLevel.WARNING,
                "Root span - op: %s, description: %s - has no timestamp set, when finishing unfinished spans.",
                root.getOperation(),
                root.getDescription());
        finishTimestamp = DateUtils.dateToSeconds(DateUtils.getCurrentDateTime());
      }
      for (final Span child : children) {
        if (!child.isFinished()) {
          child.finish(SpanStatus.DEADLINE_EXCEEDED, finishTimestamp);
        }
      }

      hub.configureScope(
          scope -> {
            scope.withTransaction(
                transaction -> {
                  if (transaction == this) {
                    scope.clearTransaction();
                  }
                });
          });
      SentryTransaction transaction = new SentryTransaction(this);
      if (transactionFinishedCallback != null) {
        transactionFinishedCallback.execute(this);
      }
      hub.captureTransaction(transaction, this.traceState(), null, profilingTraceData);
    }
  }

  @Override
  public @Nullable TraceState traceState() {
    if (hub.getOptions().isTraceSampling()) {
      synchronized (this) {
        if (traceState == null) {
          final AtomicReference<User> userAtomicReference = new AtomicReference<>();
          hub.configureScope(
              scope -> {
                userAtomicReference.set(scope.getUser());
              });
          this.traceState = new TraceState(this, userAtomicReference.get(), hub.getOptions());
        }
        return this.traceState;
      }
    } else {
      return null;
    }
  }

  @Override
  public @Nullable TraceStateHeader toTraceStateHeader() {
    final TraceState traceState = traceState();
    if (hub.getOptions().isTraceSampling() && traceState != null) {
      return TraceStateHeader.fromTraceState(
          traceState, hub.getOptions().getSerializer(), hub.getOptions().getLogger());
    } else {
      return null;
    }
  }

  private boolean hasAllChildrenFinished() {
    final List<Span> spans = new ArrayList<>(this.children);
    if (!spans.isEmpty()) {
      for (final Span span : spans) {
        if (!span.isFinished()) {
          return false;
        }
      }
    }
    return true;
  }

  @Override
  public void setOperation(final @NotNull String operation) {
    this.root.setOperation(operation);
  }

  @Override
  public @NotNull String getOperation() {
    return this.root.getOperation();
  }

  @Override
  public void setDescription(final @Nullable String description) {
    this.root.setDescription(description);
  }

  @Override
  public @Nullable String getDescription() {
    return this.root.getDescription();
  }

  @Override
  public void setStatus(final @Nullable SpanStatus status) {
    this.root.setStatus(status);
  }

  @Override
  public @Nullable SpanStatus getStatus() {
    return this.root.getStatus();
  }

  @Override
  public void setThrowable(final @Nullable Throwable throwable) {
    this.root.setThrowable(throwable);
  }

  @Override
  public @Nullable Throwable getThrowable() {
    return this.root.getThrowable();
  }

  @Override
  public @NotNull SpanContext getSpanContext() {
    return this.root.getSpanContext();
  }

  @Override
  public void setTag(final @NotNull String key, final @NotNull String value) {
    this.root.setTag(key, value);
  }

  @Override
  public @Nullable String getTag(final @NotNull String key) {
    return this.root.getTag(key);
  }

  @Override
  public boolean isFinished() {
    return this.root.isFinished();
  }

  @Override
  public void setData(@NotNull String key, @NotNull Object value) {
    this.root.setData(key, value);
  }

  @Override
  public @Nullable Object getData(@NotNull String key) {
    return this.root.getData(key);
  }

  public @Nullable Map<String, Object> getData() {
    return this.root.getData();
  }

  @Override
  public @Nullable Boolean isSampled() {
    return this.root.isSampled();
  }

  @Override
  public void setName(@NotNull String name) {
    this.name = name;
  }

  @Override
  public @NotNull String getName() {
    return this.name;
  }

  @Override
  public @NotNull List<Span> getSpans() {
    return this.children;
  }

  @Override
  public @Nullable Span getLatestActiveSpan() {
    final List<Span> spans = new ArrayList<>(this.children);
    if (!spans.isEmpty()) {
      for (int i = spans.size() - 1; i >= 0; i--) {
        if (!spans.get(i).isFinished()) {
          return spans.get(i);
        }
      }
    }
    return null;
  }

  @Override
  public @NotNull SentryId getEventId() {
    return eventId;
  }

  public @Nullable Double getHighPrecisionTimestamp() {
    return root.getHighPrecisionTimestamp();
  }

  @NotNull
  Span getRoot() {
    return root;
  }

  private static final class FinishStatus {
    static final FinishStatus NOT_FINISHED = FinishStatus.notFinished();

    private final boolean isFinishing;
    private final @Nullable SpanStatus spanStatus;

    static @NotNull FinishStatus finishing(final @Nullable SpanStatus finishStatus) {
      return new FinishStatus(true, finishStatus);
    }

    private static @NotNull FinishStatus notFinished() {
      return new FinishStatus(false, null);
    }

    private FinishStatus(final boolean isFinishing, final @Nullable SpanStatus spanStatus) {
      this.isFinishing = isFinishing;
      this.spanStatus = spanStatus;
    }
  }
}
