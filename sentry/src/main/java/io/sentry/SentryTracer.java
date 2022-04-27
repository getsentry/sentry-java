package io.sentry;

import io.sentry.protocol.SentryId;
import io.sentry.protocol.SentryTransaction;
import io.sentry.protocol.User;
import io.sentry.util.Objects;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

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

  /**
   * If `trimEnd` is true, sets the end timestamp of the transaction to the highest timestamp of
   * child spans, trimming the duration of the transaction. This is useful to discard extra time in
   * the idle transactions to trim their duration to children' duration.
   */
  private final boolean trimEnd;

  /**
   * The idle time, measured in ms, to wait until the transaction will be finished. The transaction
   * will use the end timestamp of the last finished span as the endtime for the transaction.
   *
   * <p>When set to {@code null} the transaction must be finished manually.
   *
   * <p>The default is 3 seconds.
   */
  private final @Nullable Long idleTimeout;

  private @Nullable TimerTask timerTask;
  private final @NotNull Timer timer = new Timer(true);
  private final @NotNull SpanByTimestampComparator spanByTimestampComparator =
      new SpanByTimestampComparator();
  private @Nullable CountDownLatch latch = null;

  private @Nullable TraceState traceState;

  public SentryTracer(final @NotNull TransactionContext context, final @NotNull IHub hub) {
    this(context, hub, null);
  }

  public SentryTracer(
      final @NotNull TransactionContext context,
      final @NotNull IHub hub,
      final boolean waitForChildren,
      final @Nullable TransactionFinishedCallback transactionFinishedCallback) {
    this(context, hub, null, waitForChildren, null, false, transactionFinishedCallback);
  }

  SentryTracer(
      final @NotNull TransactionContext context,
      final @NotNull IHub hub,
      final @Nullable Date startTimestamp) {
    this(context, hub, startTimestamp, false, null, false, null);
  }

  SentryTracer(
      final @NotNull TransactionContext context,
      final @NotNull IHub hub,
      final @Nullable Date startTimestamp,
      final boolean waitForChildren,
      final @Nullable Long idleTimeout,
      final boolean trimEnd,
      final @Nullable TransactionFinishedCallback transactionFinishedCallback) {
    this(
        context,
        hub,
        startTimestamp,
        waitForChildren,
        idleTimeout,
        trimEnd,
        transactionFinishedCallback,
        null);
  }

  SentryTracer(
      final @NotNull TransactionContext context,
      final @NotNull IHub hub,
      final @Nullable Date startTimestamp,
      final boolean waitForChildren,
      final @Nullable Long idleTimeout,
      final boolean trimEnd,
      final @Nullable TransactionFinishedCallback transactionFinishedCallback,
      final @Nullable CountDownLatch latch) {
    Objects.requireNonNull(context, "context is required");
    Objects.requireNonNull(hub, "hub is required");
    this.root = new Span(context, this, hub, startTimestamp);
    this.name = context.getName();
    this.hub = hub;
    this.waitForChildren = waitForChildren;
    this.idleTimeout = idleTimeout;
    this.trimEnd = trimEnd;
    this.transactionFinishedCallback = transactionFinishedCallback;
    this.latch = latch;

    if (idleTimeout != null) {
      scheduleFinish(idleTimeout, latch);
    }
  }

  @TestOnly
  void scheduleFinish(final @NotNull Long idleTimeout, final @Nullable CountDownLatch latch) {
    cancelTimer();
    timerTask =
        new TimerTask() {
          @Override
          public void run() {
            final SpanStatus status = getStatus();
            finish((status != null) ? status : SpanStatus.OK);
            if (latch != null) {
              latch.countDown();
            }
          }
        };

    timer.schedule(timerTask, idleTimeout);
  }

  @Override
  public void scheduleFinish(final @NotNull Long idleTimeout) {
    scheduleFinish(idleTimeout, null);
  }

  private void cancelTimer() {
    if (timerTask != null) {
      timerTask.cancel();
      timerTask = null;
    }
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
    if (root.isFinished()) {
      return NoOpSpan.getInstance();
    }

    Objects.requireNonNull(parentSpanId, "parentSpanId is required");
    Objects.requireNonNull(operation, "operation is required");
    cancelTimer();
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
              if (idleTimeout != null) {
                // if it's an idle transaction, no matter the status, we'll reset the timeout here
                // so the transaction will either idle and finish itself, or a new child will be
                // added and we'll wait for it again
                scheduleFinish(idleTimeout, latch);
              } else if (finishStatus.isFinishing) {
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
    if (root.isFinished()) {
      return NoOpSpan.getInstance();
    }

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

  @SuppressWarnings({"JdkObsolete", "JavaUtilDate"})
  @Override
  public void finish(@Nullable SpanStatus status) {
    this.finishStatus = FinishStatus.finishing(status);
    if (!root.isFinished() && (!waitForChildren || hasAllChildrenFinished())) {
      ProfilingTraceData profilingTraceData = null;
      Boolean isSampled = isSampled();
      if (isSampled == null) {
        isSampled = false;
      }
      if (hub.getOptions().isProfilingEnabled() && isSampled) {
        profilingTraceData = hub.getOptions().getTransactionProfiler().onTransactionFinish(this);
      }

      // try to get the high precision timestamp from the root span
      Long endTime = System.nanoTime();
      Double finishTimestamp = root.getHighPrecisionTimestamp(endTime);
      // if it's not set -> fallback to the current time
      if (finishTimestamp == null) {
        finishTimestamp = DateUtils.dateToSeconds(DateUtils.getCurrentDateTime());
        endTime = null;
      }
      // finish unfinished children
      for (final Span child : children) {
        if (!child.isFinished()) {
          child.setSpanFinishedCallback(
              null); // reset the callback, as we're already in the finish method
          child.finish(SpanStatus.DEADLINE_EXCEEDED, finishTimestamp, endTime);
        }
      }

      // set the transaction finish timestamp to the latest child timestamp, if the transaction
      // is an idle transaction
      if (!children.isEmpty() && trimEnd) {
        final Span oldestChild = Collections.max(children, spanByTimestampComparator);
        final Double oldestChildTimestamp = oldestChild.getTimestamp();
        if (oldestChildTimestamp != null && finishTimestamp > oldestChildTimestamp) {
          finishTimestamp = oldestChildTimestamp;
          endTime = oldestChild.getEndNanos();
        }
      }
      root.finish(finishStatus.spanStatus, finishTimestamp, endTime);

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
      if (children.isEmpty() && idleTimeout != null) {
        // if it's an idle transaction which has no children, we drop it to save user's quota
        return;
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
    if (root.isFinished()) {
      return;
    }

    this.root.setOperation(operation);
  }

  @Override
  public @NotNull String getOperation() {
    return this.root.getOperation();
  }

  @Override
  public void setDescription(final @Nullable String description) {
    if (root.isFinished()) {
      return;
    }

    this.root.setDescription(description);
  }

  @Override
  public @Nullable String getDescription() {
    return this.root.getDescription();
  }

  @Override
  public void setStatus(final @Nullable SpanStatus status) {
    if (root.isFinished()) {
      return;
    }

    this.root.setStatus(status);
  }

  @Override
  public @Nullable SpanStatus getStatus() {
    return this.root.getStatus();
  }

  @Override
  public void setThrowable(final @Nullable Throwable throwable) {
    if (root.isFinished()) {
      return;
    }

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
    if (root.isFinished()) {
      return;
    }

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
    if (root.isFinished()) {
      return;
    }

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
    if (root.isFinished()) {
      return;
    }

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

  @TestOnly
  @Nullable
  TimerTask getTimerTask() {
    return timerTask;
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

  private static final class SpanByTimestampComparator implements Comparator<Span> {

    @SuppressWarnings({"JdkObsolete", "JavaUtilDate"})
    @Override
    public int compare(Span o1, Span o2) {
      final Double first = o1.getHighPrecisionTimestamp();
      final Double second = o2.getHighPrecisionTimestamp();
      if (first == null) {
        return -1;
      } else if (second == null) {
        return 1;
      } else {
        return first.compareTo(second);
      }
    }
  }
}
