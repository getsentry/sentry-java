package io.sentry;

import io.sentry.protocol.Contexts;
import io.sentry.protocol.MeasurementValue;
import io.sentry.protocol.SentryId;
import io.sentry.protocol.SentryTransaction;
import io.sentry.protocol.TransactionNameSource;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
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

  private volatile @Nullable TimerTask timerTask;
  private volatile @Nullable Timer timer = null;
  private final @NotNull Object timerLock = new Object();
  private final @NotNull SpanByTimestampComparator spanByTimestampComparator =
      new SpanByTimestampComparator();
  private final @NotNull AtomicBoolean isFinishTimerRunning = new AtomicBoolean(false);

  private final @NotNull Baggage baggage;
  private @NotNull TransactionNameSource transactionNameSource;
  private final @NotNull Map<String, MeasurementValue> measurements;
  private final @NotNull Instrumenter instrumenter;
  private final @NotNull Contexts contexts = new Contexts();

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
    Objects.requireNonNull(context, "context is required");
    Objects.requireNonNull(hub, "hub is required");
    this.measurements = new ConcurrentHashMap<>();
    this.root = new Span(context, this, hub, startTimestamp);
    this.name = context.getName();
    this.instrumenter = context.getInstrumenter();
    this.hub = hub;
    this.waitForChildren = waitForChildren;
    this.idleTimeout = idleTimeout;
    this.trimEnd = trimEnd;
    this.transactionFinishedCallback = transactionFinishedCallback;
    this.transactionNameSource = context.getTransactionNameSource();

    if (context.getBaggage() != null) {
      this.baggage = context.getBaggage();
    } else {
      this.baggage = new Baggage(hub.getOptions().getLogger());
    }

    if (idleTimeout != null) {
      timer = new Timer(true);
      scheduleFinish();
    }
  }

  @Override
  public void scheduleFinish() {
    synchronized (timerLock) {
      cancelTimer();
      if (timer != null) {
        isFinishTimerRunning.set(true);
        timerTask =
            new TimerTask() {
              @Override
              public void run() {
                final SpanStatus status = getStatus();
                finish((status != null) ? status : SpanStatus.OK);
                isFinishTimerRunning.set(false);
              }
            };

        timer.schedule(timerTask, idleTimeout);
      }
    }
  }

  private void cancelTimer() {
    synchronized (timerLock) {
      if (timerTask != null) {
        timerTask.cancel();
        isFinishTimerRunning.set(false);
        timerTask = null;
      }
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
      final @Nullable Date timestamp,
      final @NotNull Instrumenter instrumenter) {
    return createChild(parentSpanId, operation, description, timestamp, instrumenter);
  }

  /**
   * Starts a child Span with given trace id and parent span id.
   *
   * @param parentSpanId - parent span id
   * @return a new transaction span
   */
  @NotNull
  private ISpan createChild(final @NotNull SpanId parentSpanId, final @NotNull String operation) {
    return createChild(parentSpanId, operation, null, null, Instrumenter.SENTRY);
  }

  @NotNull
  private ISpan createChild(
      final @NotNull SpanId parentSpanId,
      final @NotNull String operation,
      final @Nullable String description,
      @Nullable Date timestamp,
      final @NotNull Instrumenter instrumenter) {
    if (root.isFinished()) {
      return NoOpSpan.getInstance();
    }

    if (!this.instrumenter.equals(instrumenter)) {
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
                if (!waitForChildren || hasAllChildrenFinished()) {
                  scheduleFinish();
                }
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
      final @NotNull String operation,
      @Nullable String description,
      @Nullable Date timestamp,
      @NotNull Instrumenter instrumenter) {
    return createChild(operation, description, timestamp, instrumenter);
  }

  @Override
  public @NotNull ISpan startChild(
      final @NotNull String operation, final @Nullable String description) {
    return createChild(operation, description, null, Instrumenter.SENTRY);
  }

  private @NotNull ISpan createChild(
      final @NotNull String operation,
      final @Nullable String description,
      @Nullable Date timestamp,
      final @NotNull Instrumenter instrumenter) {
    if (root.isFinished()) {
      return NoOpSpan.getInstance();
    }

    if (!this.instrumenter.equals(instrumenter)) {
      return NoOpSpan.getInstance();
    }

    if (children.size() < hub.getOptions().getMaxSpans()) {
      return root.startChild(operation, description, timestamp, instrumenter);
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
    this.finish(status, null);
  }

  @SuppressWarnings({"JdkObsolete", "JavaUtilDate"})
  @Override
  @ApiStatus.Internal
  public void finish(@Nullable SpanStatus status, @Nullable Date finishDate) {
    this.finishStatus = FinishStatus.finishing(status);
    if (!root.isFinished() && (!waitForChildren || hasAllChildrenFinished())) {
      ProfilingTraceData profilingTraceData = null;
      if (Boolean.TRUE.equals(isSampled()) && Boolean.TRUE.equals(isProfileSampled())) {
        profilingTraceData = hub.getOptions().getTransactionProfiler().onTransactionFinish(this);
      }

      // try to get the high precision timestamp from the root span
      Long endTime = System.nanoTime();
      Double finishTimestamp = root.getHighPrecisionTimestamp(endTime);

      // if a finishDate was passed in, use that instead
      if (finishDate != null) {
        finishTimestamp = DateUtils.dateToSeconds(finishDate);
        endTime = null;
      }

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

      if (timer != null) {
        synchronized (timerLock) {
          if (timer != null) {
            timer.cancel();
            timer = null;
          }
        }
      }

      if (children.isEmpty() && idleTimeout != null) {
        // if it's an idle transaction which has no children, we drop it to save user's quota
        return;
      }

      transaction.getMeasurements().putAll(measurements);
      hub.captureTransaction(transaction, traceContext(), null, profilingTraceData);
    }
  }

  @Override
  public @Nullable TraceContext traceContext() {
    if (hub.getOptions().isTraceSampling()) {
      updateBaggageValues();
      return baggage.toTraceContext();
    } else {
      return null;
    }
  }

  private void updateBaggageValues() {
    synchronized (this) {
      if (baggage.isMutable()) {
        final AtomicReference<User> userAtomicReference = new AtomicReference<>();
        hub.configureScope(
            scope -> {
              userAtomicReference.set(scope.getUser());
            });
        baggage.setValuesFromTransaction(
            this, userAtomicReference.get(), hub.getOptions(), this.getSamplingDecision());
        baggage.freeze();
      }
    }
  }

  @Override
  public @Nullable BaggageHeader toBaggageHeader(@Nullable List<String> thirdPartyBaggageHeaders) {
    if (hub.getOptions().isTraceSampling()) {
      updateBaggageValues();

      return BaggageHeader.fromBaggageAndOutgoingHeader(baggage, thirdPartyBaggageHeaders);
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

  @Override
  public void setMeasurement(final @NotNull String name, final @NotNull Number value) {
    if (root.isFinished()) {
      return;
    }

    this.measurements.put(name, new MeasurementValue(value, null));
  }

  @Override
  public void setMeasurement(
      final @NotNull String name,
      final @NotNull Number value,
      final @NotNull MeasurementUnit unit) {
    if (root.isFinished()) {
      return;
    }

    this.measurements.put(name, new MeasurementValue(value, unit.apiName()));
  }

  public @Nullable Map<String, Object> getData() {
    return this.root.getData();
  }

  @Override
  public @Nullable Boolean isSampled() {
    return this.root.isSampled();
  }

  @Override
  public @Nullable Boolean isProfileSampled() {
    return this.root.isProfileSampled();
  }

  @Override
  public @Nullable TracesSamplingDecision getSamplingDecision() {
    return this.root.getSamplingDecision();
  }

  @Override
  public void setName(@NotNull String name) {
    if (root.isFinished()) {
      return;
    }

    this.name = name;
  }

  @ApiStatus.Internal
  @Override
  public void setName(@NotNull String name, @NotNull TransactionNameSource transactionNameSource) {
    this.setName(name);
    this.transactionNameSource = transactionNameSource;
  }

  @Override
  public @NotNull String getName() {
    return this.name;
  }

  @Override
  public @NotNull TransactionNameSource getTransactionNameSource() {
    return this.transactionNameSource;
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

  @TestOnly
  @Nullable
  Timer getTimer() {
    return timer;
  }

  @TestOnly
  @NotNull
  AtomicBoolean isFinishTimerRunning() {
    return isFinishTimerRunning;
  }

  @TestOnly
  @NotNull
  Map<String, MeasurementValue> getMeasurements() {
    return measurements;
  }

  @ApiStatus.Internal
  @Override
  public void setContext(final @NotNull String key, final @NotNull Object context) {
    contexts.put(key, context);
  }

  @ApiStatus.Internal
  @Override
  public @NotNull Contexts getContexts() {
    return contexts;
  }

  @Override
  public boolean isNoOp() {
    return false;
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
