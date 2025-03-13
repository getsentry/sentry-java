package io.sentry;

import io.sentry.protocol.Contexts;
import io.sentry.protocol.SentryId;
import io.sentry.protocol.SentryTransaction;
import io.sentry.protocol.TransactionNameSource;
import io.sentry.util.AutoClosableReentrantLock;
import io.sentry.util.CollectionUtils;
import io.sentry.util.Objects;
import io.sentry.util.SpanUtils;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
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
  private final @NotNull IScopes scopes;
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
  private final @NotNull AutoClosableReentrantLock timerLock = new AutoClosableReentrantLock();
  private final @NotNull AutoClosableReentrantLock tracerLock = new AutoClosableReentrantLock();

  private final @NotNull AtomicBoolean isIdleFinishTimerRunning = new AtomicBoolean(false);
  private final @NotNull AtomicBoolean isDeadlineTimerRunning = new AtomicBoolean(false);

  private @NotNull TransactionNameSource transactionNameSource;
  private final @NotNull Instrumenter instrumenter;
  private final @NotNull Contexts contexts = new Contexts();
  private final @Nullable TransactionPerformanceCollector transactionPerformanceCollector;
  private final @NotNull TransactionOptions transactionOptions;

  public SentryTracer(final @NotNull TransactionContext context, final @NotNull IScopes scopes) {
    this(context, scopes, new TransactionOptions(), null);
  }

  public SentryTracer(
      final @NotNull TransactionContext context,
      final @NotNull IScopes scopes,
      final @NotNull TransactionOptions transactionOptions) {
    this(context, scopes, transactionOptions, null);
  }

  SentryTracer(
      final @NotNull TransactionContext context,
      final @NotNull IScopes scopes,
      final @NotNull TransactionOptions transactionOptions,
      final @Nullable TransactionPerformanceCollector transactionPerformanceCollector) {
    Objects.requireNonNull(context, "context is required");
    Objects.requireNonNull(scopes, "scopes are required");

    this.root = new Span(context, this, scopes, transactionOptions);

    this.name = context.getName();
    this.instrumenter = context.getInstrumenter();
    this.scopes = scopes;
    this.transactionPerformanceCollector = transactionPerformanceCollector;
    this.transactionNameSource = context.getTransactionNameSource();
    this.transactionOptions = transactionOptions;

    // We are currently sending the performance data only in profiles, but we are always sending
    // performance measurements.
    if (transactionPerformanceCollector != null) {
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
    try (final @NotNull ISentryLifecycleToken ignored = timerLock.acquire()) {
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
            scopes
                .getOptions()
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

    final @NotNull SentryDate finishTimestamp = scopes.getOptions().getDateProvider().now();

    // abort all child-spans first, this ensures the transaction can be finished,
    // even if waitForChildren is true
    // iterate in reverse order to ensure leaf spans are processed before their parents
    @NotNull
    final ListIterator<Span> iterator =
        CollectionUtils.reverseListIterator((CopyOnWriteArrayList<Span>) this.children);
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
      finishTimestamp = scopes.getOptions().getDateProvider().now();
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

      final @NotNull AtomicReference<List<PerformanceCollectionData>> performanceCollectionData =
          new AtomicReference<>();
      // We set the new spanFinishedCallback here instead of creation time, calling the old one to
      // avoid the user overwrites it by setting a custom spanFinishedCallback on the root span
      final @Nullable SpanFinishedCallback oldCallback = this.root.getSpanFinishedCallback();
      this.root.setSpanFinishedCallback(
          span -> {
            if (oldCallback != null) {
              oldCallback.execute(span);
            }

            // Let's call the finishCallback here, when the root span has a finished date but it's
            // not finished, yet
            final @Nullable TransactionFinishedCallback finishedCallback =
                transactionOptions.getTransactionFinishedCallback();
            if (finishedCallback != null) {
              finishedCallback.execute(this);
            }

            if (transactionPerformanceCollector != null) {
              performanceCollectionData.set(transactionPerformanceCollector.stop(this));
            }
          });

      // any un-finished childs will remain unfinished
      // as relay takes care of setting the end-timestamp + deadline_exceeded
      // see
      // https://github.com/getsentry/relay/blob/40697d0a1c54e5e7ad8d183fc7f9543b94fe3839/relay-general/src/store/transactions/processor.rs#L374-L378
      root.finish(finishStatus.spanStatus, finishTimestamp);

      ProfilingTraceData profilingTraceData = null;
      if (Boolean.TRUE.equals(isSampled()) && Boolean.TRUE.equals(isProfileSampled())) {
        profilingTraceData =
            scopes
                .getOptions()
                .getTransactionProfiler()
                .onTransactionFinish(this, performanceCollectionData.get(), scopes.getOptions());
      }
      if (performanceCollectionData.get() != null) {
        performanceCollectionData.get().clear();
      }

      scopes.configureScope(
          scope -> {
            scope.withTransaction(
                transaction -> {
                  if (transaction == this) {
                    scope.clearTransaction();
                  }
                });
          });
      final SentryTransaction transaction = new SentryTransaction(this);

      if (timer != null) {
        try (final @NotNull ISentryLifecycleToken ignored = timerLock.acquire()) {
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
        scopes
            .getOptions()
            .getLogger()
            .log(
                SentryLevel.DEBUG,
                "Dropping idle transaction %s because it has no child spans",
                name);
        return;
      }

      transaction.getMeasurements().putAll(root.getMeasurements());
      scopes.captureTransaction(transaction, traceContext(), hint, profilingTraceData);
    }
  }

  private void cancelIdleTimer() {
    try (final @NotNull ISentryLifecycleToken ignored = timerLock.acquire()) {
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
      try (final @NotNull ISentryLifecycleToken ignored = timerLock.acquire()) {
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
            scopes
                .getOptions()
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
    try (final @NotNull ISentryLifecycleToken ignored = timerLock.acquire()) {
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
    final @NotNull SpanContext spanContext =
        getSpanContext().copyForChild(operation, parentSpanId, null);
    spanContext.setDescription(description);
    spanContext.setInstrumenter(instrumenter);

    final @NotNull SpanOptions spanOptions = new SpanOptions();
    spanOptions.setStartTimestamp(timestamp);

    return createChild(spanContext, spanOptions);
  }

  @NotNull
  ISpan startChild(
      final @NotNull SpanId parentSpanId,
      final @NotNull String operation,
      final @Nullable String description,
      final @Nullable SentryDate timestamp,
      final @NotNull Instrumenter instrumenter,
      final @NotNull SpanOptions spanOptions) {
    final @NotNull SpanContext spanContext =
        getSpanContext().copyForChild(operation, parentSpanId, null);
    spanContext.setDescription(description);
    spanContext.setInstrumenter(instrumenter);

    spanOptions.setStartTimestamp(timestamp);

    return createChild(spanContext, spanOptions);
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
    final @NotNull SpanContext spanContext =
        getSpanContext().copyForChild(operation, parentSpanId, null);
    spanContext.setDescription(description);
    spanContext.setInstrumenter(Instrumenter.SENTRY);

    return createChild(spanContext, options);
  }

  @NotNull
  private ISpan createChild(
      final @NotNull SpanContext spanContext, final @NotNull SpanOptions spanOptions) {
    if (root.isFinished()) {
      return NoOpSpan.getInstance();
    }

    if (!this.instrumenter.equals(spanContext.getInstrumenter())) {
      return NoOpSpan.getInstance();
    }

    if (SpanUtils.isIgnored(scopes.getOptions().getIgnoredSpanOrigins(), spanOptions.getOrigin())) {
      return NoOpSpan.getInstance();
    }

    final @Nullable SpanId parentSpanId = spanContext.getParentSpanId();
    final @NotNull String operation = spanContext.getOperation();
    final @Nullable String description = spanContext.getDescription();

    if (children.size() < scopes.getOptions().getMaxSpans()) {
      Objects.requireNonNull(parentSpanId, "parentSpanId is required");
      Objects.requireNonNull(operation, "operation is required");
      cancelIdleTimer();
      final Span span =
          new Span(
              this,
              scopes,
              spanContext,
              spanOptions,
              finishingSpan -> {
                if (transactionPerformanceCollector != null) {
                  transactionPerformanceCollector.onSpanFinished(finishingSpan);
                }
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
      // TODO [POTEL] missing features
      //      final Span span =
      //          new Span(
      //              root.getTraceId(),
      //              parentSpanId,
      //              this,
      //              operation,
      //              this.scopes,
      //              timestamp,
      //              spanOptions,
      //              finishingSpan -> {
      //                if (transactionPerformanceCollector != null) {
      //                  transactionPerformanceCollector.onSpanFinished(finishingSpan);
      //                }
      //                final FinishStatus finishStatus = this.finishStatus;
      //                if (transactionOptions.getIdleTimeout() != null) {
      //                  // if it's an idle transaction, no matter the status, we'll reset the
      // timeout here
      //                  // so the transaction will either idle and finish itself, or a new child
      // will be
      //                  // added and we'll wait for it again
      //                  if (!transactionOptions.isWaitForChildren() || hasAllChildrenFinished()) {
      //                    scheduleFinish();
      //                  }
      //                } else if (finishStatus.isFinishing) {
      //                  finish(finishStatus.spanStatus);
      //                }
      //              });
      //      span.setDescription(description);
      final long threadId = scopes.getOptions().getThreadChecker().currentThreadSystemId();
      span.setData(SpanDataConvention.THREAD_ID, String.valueOf(threadId));
      span.setData(
          SpanDataConvention.THREAD_NAME,
          scopes.getOptions().getThreadChecker().isMainThread()
              ? "main"
              : Thread.currentThread().getName());
      this.children.add(span);
      if (transactionPerformanceCollector != null) {
        transactionPerformanceCollector.onSpanStarted(span);
      }
      return span;
    } else {
      scopes
          .getOptions()
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

  @Override
  public @NotNull ISpan startChild(
      @NotNull SpanContext spanContext, @NotNull SpanOptions spanOptions) {
    return createChild(spanContext, spanOptions);
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

    if (children.size() < scopes.getOptions().getMaxSpans()) {
      return root.startChild(operation, description, timestamp, instrumenter, spanOptions);
    } else {
      scopes
          .getOptions()
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
    if (scopes.getOptions().isTraceSampling()) {
      final @Nullable Baggage baggage = getSpanContext().getBaggage();
      if (baggage != null) {
        updateBaggageValues(baggage);
        return baggage.toTraceContext();
      }
    }
    return null;
  }

  private void updateBaggageValues(final @NotNull Baggage baggage) {
    try (final @NotNull ISentryLifecycleToken ignored = tracerLock.acquire()) {
      if (baggage.isMutable()) {
        final AtomicReference<SentryId> replayId = new AtomicReference<>();
        scopes.configureScope(
            scope -> {
              replayId.set(scope.getReplayId());
            });
        baggage.setValuesFromTransaction(
            getSpanContext().getTraceId(),
            replayId.get(),
            scopes.getOptions(),
            this.getSamplingDecision(),
            getName(),
            getTransactionNameSource());
        baggage.freeze();
      }
    }
  }

  @Override
  public @Nullable BaggageHeader toBaggageHeader(@Nullable List<String> thirdPartyBaggageHeaders) {
    if (scopes.getOptions().isTraceSampling()) {
      final @Nullable Baggage baggage = getSpanContext().getBaggage();
      if (baggage != null) {
        updateBaggageValues(baggage);
        return BaggageHeader.fromBaggageAndOutgoingHeader(baggage, thirdPartyBaggageHeaders);
      }
    }
    return null;
  }

  private boolean hasAllChildrenFinished() {
    @NotNull final ListIterator<Span> iterator = this.children.listIterator();
    while (iterator.hasNext()) {
      @NotNull final Span span = iterator.next();
      // This is used in the spanFinishCallback, when the span isn't finished, but has a finish
      // date
      if (!span.isFinished() && span.getFinishDate() == null) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void setOperation(final @NotNull String operation) {
    if (root.isFinished()) {
      scopes
          .getOptions()
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "The transaction is already finished. Operation %s cannot be set",
              operation);
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
      scopes
          .getOptions()
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "The transaction is already finished. Description %s cannot be set",
              description);
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
      scopes
          .getOptions()
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "The transaction is already finished. Status %s cannot be set",
              status == null ? "null" : status.name());
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
      scopes
          .getOptions()
          .getLogger()
          .log(SentryLevel.DEBUG, "The transaction is already finished. Throwable cannot be set");
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
      scopes
          .getOptions()
          .getLogger()
          .log(SentryLevel.DEBUG, "The transaction is already finished. Tag %s cannot be set", key);
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
      scopes
          .getOptions()
          .getLogger()
          .log(
              SentryLevel.DEBUG, "The transaction is already finished. Data %s cannot be set", key);
      return;
    }

    this.root.setData(key, value);
  }

  @Override
  public @Nullable Object getData(@NotNull String key) {
    return this.root.getData(key);
  }

  @ApiStatus.Internal
  public void setMeasurementFromChild(final @NotNull String name, final @NotNull Number value) {
    // We don't want to overwrite the root span measurement, if it comes from a child.
    if (!root.getMeasurements().containsKey(name)) {
      setMeasurement(name, value);
    }
  }

  @ApiStatus.Internal
  public void setMeasurementFromChild(
      final @NotNull String name,
      final @NotNull Number value,
      final @NotNull MeasurementUnit unit) {
    // We don't want to overwrite the root span measurement, if it comes from a child.
    if (!root.getMeasurements().containsKey(name)) {
      setMeasurement(name, value, unit);
    }
  }

  @Override
  public void setMeasurement(final @NotNull String name, final @NotNull Number value) {
    root.setMeasurement(name, value);
  }

  @Override
  public void setMeasurement(
      final @NotNull String name,
      final @NotNull Number value,
      final @NotNull MeasurementUnit unit) {
    root.setMeasurement(name, value, unit);
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
      scopes
          .getOptions()
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "The transaction is already finished. Name %s cannot be set",
              name);
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
  public @Nullable ISpan getLatestActiveSpan() {
    @NotNull
    final ListIterator<Span> iterator =
        CollectionUtils.reverseListIterator((CopyOnWriteArrayList<Span>) this.children);
    while (iterator.hasPrevious()) {
      @NotNull final Span span = iterator.previous();
      if (!span.isFinished()) {
        return span;
      }
    }
    return null;
  }

  @Override
  public @NotNull SentryId getEventId() {
    return eventId;
  }

  @ApiStatus.Internal
  @Override
  public @NotNull ISentryLifecycleToken makeCurrent() {
    scopes.configureScope(
        (scope) -> {
          scope.setTransaction(this);
        });

    return NoOpScopesLifecycleToken.getInstance();
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
