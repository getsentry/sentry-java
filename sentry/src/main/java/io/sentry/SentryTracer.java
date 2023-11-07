package io.sentry;

import io.sentry.protocol.Contexts;
import io.sentry.protocol.MeasurementValue;
import io.sentry.protocol.SentryId;
import io.sentry.protocol.SentryTransaction;
import io.sentry.protocol.TransactionNameSource;
import io.sentry.protocol.User;
import io.sentry.util.Objects;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
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
   * Holds the status for finished tracer. Tracer can have finishedStatus set, but not be finished
   * itself when `waitForChildren` is set to `true`, `#finish()` method was called but there are
   * unfinished children spans.
   */
  private @NotNull FinishStatus finishStatus = FinishStatus.NOT_FINISHED;

  private volatile @Nullable TimerTask idleTimeoutTask;
  private volatile @Nullable TimerTask deadlineTimeoutTask;

  private volatile @Nullable Timer timer = null;
  private final @NotNull Object timerLock = new Object();

  private final @NotNull AtomicBoolean isIdleFinishTimerRunning = new AtomicBoolean(false);
  private final @NotNull AtomicBoolean isDeadlineTimerRunning = new AtomicBoolean(false);

  private final @NotNull Baggage baggage;
  private @NotNull TransactionNameSource transactionNameSource;
  private final @NotNull Map<String, MeasurementValue> measurements;
  private final @NotNull Instrumenter instrumenter;
  private final @NotNull Contexts contexts = new Contexts();
  private final @Nullable TransactionPerformanceCollector transactionPerformanceCollector;
  private final @NotNull TransactionOptions transactionOptions;

  public SentryTracer(final @NotNull TransactionContext context, final @NotNull IHub hub) {
    this(context, hub, new TransactionOptions(), null);
  }

  public SentryTracer(
      final @NotNull TransactionContext context,
      final @NotNull IHub hub,
      final @NotNull TransactionOptions transactionOptions) {
    this(context, hub, transactionOptions, null);
  }

  SentryTracer(
      final @NotNull TransactionContext context,
      final @NotNull IHub hub,
      final @NotNull TransactionOptions transactionOptions,
      final @Nullable TransactionPerformanceCollector transactionPerformanceCollector) {
    Objects.requireNonNull(context, "context is required");
    Objects.requireNonNull(hub, "hub is required");
    this.measurements = new ConcurrentHashMap<>();

    this.root =
        new Span(context, this, hub, transactionOptions.getStartTimestamp(), transactionOptions);

    this.name = context.getName();
    this.instrumenter = context.getInstrumenter();
    this.hub = hub;
    this.transactionPerformanceCollector = transactionPerformanceCollector;
    this.transactionNameSource = context.getTransactionNameSource();
    this.transactionOptions = transactionOptions;

    if (context.getBaggage() != null) {
      this.baggage = context.getBaggage();
    } else {
      this.baggage = new Baggage(hub.getOptions().getLogger());
    }

    // We are currently sending the performance data only in profiles, so there's no point in
    // collecting them if a profile is not sampled
    if (transactionPerformanceCollector != null && Boolean.TRUE.equals(isProfileSampled())) {
      transactionPerformanceCollector.start(this);
    }

    if (transactionOptions.getIdleTimeout() != null
        || transactionOptions.getDeadlineTimeout() != null) {
      timer = new Timer(true);

      scheduleDeadlineTimeout();
      scheduleFinish();
    }
  }

  @Override
  public void scheduleFinish() {
    synchronized (timerLock) {
      if (timer != null) {
        final @Nullable Long idleTimeout = transactionOptions.getIdleTimeout();

        if (idleTimeout != null) {
          cancelIdleTimer();
          isIdleFinishTimerRunning.set(true);
          idleTimeoutTask =
              new TimerTask() {
                @Override
                public void run() {
                  onIdleTimeoutReached();
                }
              };

          try {
            timer.schedule(idleTimeoutTask, idleTimeout);
          } catch (Throwable e) {
            hub.getOptions()
                .getLogger()
                .log(SentryLevel.WARNING, "Failed to schedule finish timer", e);
            // if we failed to schedule the finish timer for some reason, we finish it here right
            // away
            onIdleTimeoutReached();
          }
        }
      }
    }
  }

  private void onIdleTimeoutReached() {
    final @Nullable SpanStatus status = getStatus();
    finish((status != null) ? status : SpanStatus.OK);
    isIdleFinishTimerRunning.set(false);
  }

  private void onDeadlineTimeoutReached() {
    final @Nullable SpanStatus status = getStatus();
    forceFinish(
        (status != null) ? status : SpanStatus.DEADLINE_EXCEEDED,
        transactionOptions.getIdleTimeout() != null,
        null);
    isDeadlineTimerRunning.set(false);
  }

  @Override
  public @NotNull void forceFinish(
      final @NotNull SpanStatus status, final boolean dropIfNoChildren, final @Nullable Hint hint) {
    if (isFinished()) {
      return;
    }

    final @NotNull SentryDate finishTimestamp = hub.getOptions().getDateProvider().now();

    // abort all child-spans first, this ensures the transaction can be finished,
    // even if waitForChildren is true
    // iterate in reverse order to ensure leaf spans are processed before their parents
    @NotNull final ListIterator<Span> iterator = children.listIterator(children.size());
    while (iterator.hasPrevious()) {
      @NotNull final Span span = iterator.previous();
      span.setSpanFinishedCallback(null);
      span.finish(status, finishTimestamp);
    }
    finish(status, finishTimestamp, dropIfNoChildren, hint);
  }

  @Override
  public void finish(
      @Nullable SpanStatus status,
      @Nullable SentryDate finishDate,
      boolean dropIfNoChildren,
      @Nullable Hint hint) {
    // try to get the high precision timestamp from the root span
    SentryDate finishTimestamp = root.getFinishDate();

    // if a finishDate was passed in, use that instead
    if (finishDate != null) {
      finishTimestamp = finishDate;
    }

    // if it's not set -> fallback to the current time
    if (finishTimestamp == null) {
      finishTimestamp = hub.getOptions().getDateProvider().now();
    }

    // auto-finish any idle spans first
    for (final Span span : children) {
      if (span.getOptions().isIdle()) {
        span.finish((status != null) ? status : getSpanContext().status, finishTimestamp);
      }
    }

    this.finishStatus = FinishStatus.finishing(status);
    if (!root.isFinished()
        && (!transactionOptions.isWaitForChildren() || hasAllChildrenFinished())) {
      List<PerformanceCollectionData> performanceCollectionData = null;
      if (transactionPerformanceCollector != null) {
        performanceCollectionData = transactionPerformanceCollector.stop(this);
      }

      ProfilingTraceData profilingTraceData = null;
      if (Boolean.TRUE.equals(isSampled()) && Boolean.TRUE.equals(isProfileSampled())) {
        profilingTraceData =
            hub.getOptions()
                .getTransactionProfiler()
                .onTransactionFinish(this, performanceCollectionData);
      }
      if (performanceCollectionData != null) {
        performanceCollectionData.clear();
      }

      // any un-finished childs will remain unfinished
      // as relay takes care of setting the end-timestamp + deadline_exceeded
      // see
      // https://github.com/getsentry/relay/blob/40697d0a1c54e5e7ad8d183fc7f9543b94fe3839/relay-general/src/store/transactions/processor.rs#L374-L378

      root.finish(finishStatus.spanStatus, finishTimestamp);

      hub.configureScope(
          scope -> {
            scope.withTransaction(
                transaction -> {
                  if (transaction == this) {
                    scope.clearTransaction();
                  }
                });
          });
      final SentryTransaction transaction = new SentryTransaction(this);
      final TransactionFinishedCallback finishedCallback =
          transactionOptions.getTransactionFinishedCallback();
      if (finishedCallback != null) {
        finishedCallback.execute(this);
      }

      if (timer != null) {
        synchronized (timerLock) {
          if (timer != null) {
            cancelIdleTimer();
            cancelDeadlineTimer();
            timer.cancel();
            timer = null;
          }
        }
      }

      if (dropIfNoChildren && children.isEmpty() && transactionOptions.getIdleTimeout() != null) {
        // if it's an idle transaction which has no children, we drop it to save user's quota
        hub.getOptions()
            .getLogger()
            .log(
                SentryLevel.DEBUG,
                "Dropping idle transaction %s because it has no child spans",
                name);
        return;
      }

      transaction.getMeasurements().putAll(measurements);
      hub.captureTransaction(transaction, traceContext(), hint, profilingTraceData);
    }
  }

  private void cancelIdleTimer() {
    synchronized (timerLock) {
      if (idleTimeoutTask != null) {
        idleTimeoutTask.cancel();
        isIdleFinishTimerRunning.set(false);
        idleTimeoutTask = null;
      }
    }
  }

  private void scheduleDeadlineTimeout() {
    final @Nullable Long deadlineTimeOut = transactionOptions.getDeadlineTimeout();
    if (deadlineTimeOut != null) {
      synchronized (timerLock) {
        if (timer != null) {
          cancelDeadlineTimer();
          isDeadlineTimerRunning.set(true);
          deadlineTimeoutTask =
              new TimerTask() {
                @Override
                public void run() {
                  onDeadlineTimeoutReached();
                }
              };
          try {
            timer.schedule(deadlineTimeoutTask, deadlineTimeOut);
          } catch (Throwable e) {
            hub.getOptions()
                .getLogger()
                .log(SentryLevel.WARNING, "Failed to schedule finish timer", e);
            // if we failed to schedule the finish timer for some reason, we finish it here right
            // away
            onDeadlineTimeoutReached();
          }
        }
      }
    }
  }

  private void cancelDeadlineTimer() {
    synchronized (timerLock) {
      if (deadlineTimeoutTask != null) {
        deadlineTimeoutTask.cancel();
        isDeadlineTimerRunning.set(false);
        deadlineTimeoutTask = null;
      }
    }
  }

  public @NotNull List<Span> getChildren() {
    return children;
  }

  @Override
  public @NotNull SentryDate getStartDate() {
    return this.root.getStartDate();
  }

  @Override
  public @Nullable SentryDate getFinishDate() {
    return this.root.getFinishDate();
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
    return startChild(parentSpanId, operation, description, new SpanOptions());
  }

  /**
   * Starts a child Span with given trace id and parent span id.
   *
   * @param parentSpanId - parent span id
   * @param operation - span operation name
   * @param description - span description
   * @param spanOptions - span options
   * @return a new transaction span
   */
  @NotNull
  ISpan startChild(
      final @NotNull SpanId parentSpanId,
      final @NotNull String operation,
      final @Nullable String description,
      final @NotNull SpanOptions spanOptions) {
    return createChild(parentSpanId, operation, description, spanOptions);
  }

  @NotNull
  ISpan startChild(
      final @NotNull SpanId parentSpanId,
      final @NotNull String operation,
      final @Nullable String description,
      final @Nullable SentryDate timestamp,
      final @NotNull Instrumenter instrumenter) {
    return createChild(
        parentSpanId, operation, description, timestamp, instrumenter, new SpanOptions());
  }

  @NotNull
  ISpan startChild(
      final @NotNull SpanId parentSpanId,
      final @NotNull String operation,
      final @Nullable String description,
      final @Nullable SentryDate timestamp,
      final @NotNull Instrumenter instrumenter,
      final @NotNull SpanOptions spanOptions) {
    return createChild(parentSpanId, operation, description, timestamp, instrumenter, spanOptions);
  }

  /**
   * Starts a child Span with given trace id and parent span id.
   *
   * @param parentSpanId - parent span id
   * @param operation - the span operation
   * @param description - the optional span description
   * @param options - span options
   * @return a new transaction span
   */
  @NotNull
  private ISpan createChild(
      final @NotNull SpanId parentSpanId,
      final @NotNull String operation,
      final @Nullable String description,
      final @NotNull SpanOptions options) {
    return createChild(parentSpanId, operation, description, null, Instrumenter.SENTRY, options);
  }

  @NotNull
  private ISpan createChild(
      final @NotNull SpanId parentSpanId,
      final @NotNull String operation,
      final @Nullable String description,
      @Nullable SentryDate timestamp,
      final @NotNull Instrumenter instrumenter,
      final @NotNull SpanOptions spanOptions) {
    if (root.isFinished()) {
      return NoOpSpan.getInstance();
    }

    if (!this.instrumenter.equals(instrumenter)) {
      return NoOpSpan.getInstance();
    }

    Objects.requireNonNull(parentSpanId, "parentSpanId is required");
    Objects.requireNonNull(operation, "operation is required");
    cancelIdleTimer();
    final Span span =
        new Span(
            root.getTraceId(),
            parentSpanId,
            this,
            operation,
            this.hub,
            timestamp,
            spanOptions,
            __ -> {
              final FinishStatus finishStatus = this.finishStatus;
              if (transactionOptions.getIdleTimeout() != null) {
                // if it's an idle transaction, no matter the status, we'll reset the timeout here
                // so the transaction will either idle and finish itself, or a new child will be
                // added and we'll wait for it again
                if (!transactionOptions.isWaitForChildren() || hasAllChildrenFinished()) {
                  scheduleFinish();
                }
              } else if (finishStatus.isFinishing) {
                finish(finishStatus.spanStatus);
              }
            });
    span.setDescription(description);
    span.setData(SpanDataConvention.THREAD_ID, String.valueOf(Thread.currentThread().getId()));
    span.setData(
        SpanDataConvention.THREAD_NAME,
        hub.getOptions().getMainThreadChecker().isMainThread()
            ? "main"
            : Thread.currentThread().getName());
    this.children.add(span);
    return span;
  }

  @Override
  public @NotNull ISpan startChild(final @NotNull String operation) {
    return this.startChild(operation, (String) null);
  }

  @Override
  public @NotNull ISpan startChild(
      @NotNull String operation,
      @Nullable String description,
      @Nullable SentryDate timestamp,
      @NotNull Instrumenter instrumenter) {
    return startChild(operation, description, timestamp, instrumenter, new SpanOptions());
  }

  @Override
  public @NotNull ISpan startChild(
      final @NotNull String operation,
      @Nullable String description,
      @Nullable SentryDate timestamp,
      @NotNull Instrumenter instrumenter,
      @NotNull SpanOptions spanOptions) {
    return createChild(operation, description, timestamp, instrumenter, spanOptions);
  }

  @Override
  public @NotNull ISpan startChild(
      final @NotNull String operation,
      @Nullable String description,
      @Nullable SentryDate timestamp) {
    return createChild(operation, description, timestamp, Instrumenter.SENTRY, new SpanOptions());
  }

  @Override
  public @NotNull ISpan startChild(
      final @NotNull String operation, final @Nullable String description) {
    return startChild(operation, description, null, Instrumenter.SENTRY, new SpanOptions());
  }

  @Override
  public @NotNull ISpan startChild(
      @NotNull String operation, @Nullable String description, @NotNull SpanOptions spanOptions) {
    return createChild(operation, description, null, Instrumenter.SENTRY, spanOptions);
  }

  private @NotNull ISpan createChild(
      final @NotNull String operation,
      final @Nullable String description,
      @Nullable SentryDate timestamp,
      final @NotNull Instrumenter instrumenter,
      final @NotNull SpanOptions spanOptions) {
    if (root.isFinished()) {
      return NoOpSpan.getInstance();
    }

    if (!this.instrumenter.equals(instrumenter)) {
      return NoOpSpan.getInstance();
    }

    if (children.size() < hub.getOptions().getMaxSpans()) {
      return root.startChild(operation, description, timestamp, instrumenter, spanOptions);
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
  public void finish(@Nullable SpanStatus status, @Nullable SentryDate finishDate) {
    finish(status, finishDate, true, null);
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
    setName(name, TransactionNameSource.CUSTOM);
  }

  @ApiStatus.Internal
  @Override
  public void setName(@NotNull String name, @NotNull TransactionNameSource transactionNameSource) {
    if (root.isFinished()) {
      return;
    }

    this.name = name;
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

  @NotNull
  Span getRoot() {
    return root;
  }

  @TestOnly
  @Nullable
  TimerTask getIdleTimeoutTask() {
    return idleTimeoutTask;
  }

  @TestOnly
  @Nullable
  TimerTask getDeadlineTimeoutTask() {
    return deadlineTimeoutTask;
  }

  @TestOnly
  @Nullable
  Timer getTimer() {
    return timer;
  }

  @TestOnly
  @NotNull
  AtomicBoolean isFinishTimerRunning() {
    return isIdleFinishTimerRunning;
  }

  @TestOnly
  @NotNull
  AtomicBoolean isDeadlineTimerRunning() {
    return isDeadlineTimerRunning;
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
  public boolean updateEndDate(final @NotNull SentryDate date) {
    return root.updateEndDate(date);
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
}
