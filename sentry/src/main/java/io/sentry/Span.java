package io.sentry;

import io.sentry.protocol.Contexts;
import io.sentry.protocol.MeasurementValue;
import io.sentry.protocol.SentryId;
import io.sentry.time.AnchoredClock;
import io.sentry.time.Timestamp;
import io.sentry.util.Objects;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class Span implements ISpan {

  /**
   * When the span started and ended, projected from the transaction's {@link AnchoredClock} — or
   * stated directly, when a caller supplied them.
   *
   * <p>One anchor per transaction is what makes {@code end - start} a tick difference rather than
   * the difference of two independent wall-clock readings.
   */
  private @NotNull Timestamp start;

  private @Nullable Timestamp end;

  private final @NotNull SpanContext context;

  /**
   * A transaction this span is attached to. Marked as transient to be ignored during JSON
   * serialization.
   */
  private final @NotNull SentryTracer transaction;

  /** A throwable thrown during the execution of the span. */
  private @Nullable Throwable throwable;

  private final @NotNull IScopes scopes;

  private boolean finished = false;

  private final @NotNull AtomicBoolean isFinishing = new AtomicBoolean(false);

  private final @NotNull SpanOptions options;

  private @Nullable SpanFinishedCallback spanFinishedCallback;

  private final @NotNull Map<String, Object> data = new ConcurrentHashMap<>();
  private final @NotNull Map<String, MeasurementValue> measurements = new ConcurrentHashMap<>();

  private final @NotNull Contexts contexts = new Contexts();

  Span(
      final @NotNull SentryTracer transaction,
      final @NotNull IScopes scopes,
      final @NotNull SpanContext spanContext,
      final @NotNull SpanOptions options,
      final @Nullable SpanFinishedCallback spanFinishedCallback) {
    this.context = spanContext;
    this.context.setOrigin(options.getOrigin());
    this.transaction = Objects.requireNonNull(transaction, "transaction is required");
    this.scopes = Objects.requireNonNull(scopes, "Scopes are required");
    this.options = options;
    this.spanFinishedCallback = spanFinishedCallback;
    this.start = resolveStart(options, transaction);
  }

  public Span(
      final @NotNull TransactionContext context,
      final @NotNull SentryTracer sentryTracer,
      final @NotNull IScopes scopes,
      final @NotNull SpanOptions options) {
    this.context = Objects.requireNonNull(context, "context is required");
    this.context.setOrigin(options.getOrigin());
    this.transaction = Objects.requireNonNull(sentryTracer, "sentryTracer is required");
    this.scopes = Objects.requireNonNull(scopes, "scopes are required");
    this.spanFinishedCallback = null;
    this.start = resolveStart(options, sentryTracer);
    this.options = options;
  }

  private static @NotNull Timestamp resolveStart(
      final @NotNull SpanOptions options, final @NotNull SentryTracer transaction) {
    final @Nullable SentryDate stated = options.getStartTimestamp();
    return stated != null
        ? Timestamp.ofEpochNanos(stated.nanoTimestamp())
        : transaction.getAnchor().now();
  }

  @Override
  public @NotNull SentryDate getStartDate() {
    return new SentryLongDate(start.epochNanos());
  }

  @Override
  public @Nullable SentryDate getFinishDate() {
    return end == null ? null : new SentryLongDate(end.epochNanos());
  }

  @Override
  public @NotNull Timestamp startTimestamp() {
    return start;
  }

  @Override
  public @Nullable Timestamp endTimestamp() {
    return end;
  }

  @Override
  public @Nullable AnchoredClock anchor() {
    // Both endpoints have to come from the anchor for a tick to mean anything; a stated one does
    // not, and a projected end paired with a stated start would place the span wrongly.
    final boolean anchored =
        start.anchor() != null && (end == null || end.anchor() == start.anchor());
    return anchored ? start.anchor() : null;
  }

  @Override
  public @NotNull ISpan startChild(final @NotNull String operation) {
    return this.startChild(operation, (String) null);
  }

  @Override
  public @NotNull ISpan startChild(
      final @NotNull String operation,
      final @Nullable String description,
      final @Nullable SentryDate timestamp,
      final @NotNull Instrumenter instrumenter,
      @NotNull SpanOptions spanOptions) {
    if (finished) {
      return NoOpSpan.getInstance();
    }

    return transaction.startChild(
        context.getSpanId(), operation, description, timestamp, instrumenter, spanOptions);
  }

  @Override
  public @NotNull ISpan startChild(
      @NotNull String operation, @Nullable String description, @Nullable SentryDate timestamp) {
    return startChild(operation, description, timestamp, Instrumenter.SENTRY);
  }

  @Override
  public @NotNull ISpan startChild(
      final @NotNull String operation, final @Nullable String description) {
    if (finished) {
      return NoOpSpan.getInstance();
    }

    return transaction.startChild(context.getSpanId(), operation, description);
  }

  @Override
  public @NotNull ISpan startChild(
      @NotNull String operation, @Nullable String description, @NotNull SpanOptions spanOptions) {
    if (finished) {
      return NoOpSpan.getInstance();
    }
    return transaction.startChild(context.getSpanId(), operation, description, spanOptions);
  }

  @Override
  public @NotNull ISpan startChild(
      @NotNull SpanContext spanContext, @NotNull SpanOptions spanOptions) {
    return transaction.startChild(spanContext, spanOptions);
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
  public @NotNull SentryTraceHeader toSentryTrace() {
    return new SentryTraceHeader(context.getTraceId(), context.getSpanId(), context.getSampled());
  }

  @Override
  public @Nullable TraceContext traceContext() {
    return transaction.traceContext();
  }

  @Override
  public @Nullable BaggageHeader toBaggageHeader(@Nullable List<String> thirdPartyBaggageHeaders) {
    return transaction.toBaggageHeader(thirdPartyBaggageHeaders);
  }

  @Override
  public void finish() {
    this.finish(this.context.getStatus());
  }

  @Override
  public void finish(@Nullable SpanStatus status) {
    finish(status, (Timestamp) null);
  }

  /**
   * Used to finish unfinished spans by {@link SentryTracer}.
   *
   * @param status - status to finish span with
   * @param timestamp - the root span timestamp.
   */
  @Override
  public void finish(final @Nullable SpanStatus status, final @Nullable SentryDate timestamp) {
    finish(status, timestamp == null ? null : Timestamp.ofEpochNanos(timestamp.nanoTimestamp()));
  }

  /**
   * The anchored counterpart of {@link #finish(SpanStatus, SentryDate)}, used by {@link
   * SentryTracer} so that a span it stamps keeps the transaction's anchor rather than being demoted
   * to a stated instant.
   */
  void finish(final @Nullable SpanStatus status, final @Nullable Timestamp end) {
    // the span can be finished only once
    if (finished || !isFinishing.compareAndSet(false, true)) {
      return;
    }

    this.context.setStatus(status);
    this.end = end == null ? transaction.getAnchor().now() : end;
    if (options.isTrimStart() || options.isTrimEnd()) {
      @Nullable Timestamp minChildStart = null;
      @Nullable Timestamp maxChildEnd = null;

      // The root span should be trimmed based on all children, but the other spans, like the
      // jetpack composition should be trimmed based on its direct children only
      final @NotNull List<Span> children =
          transaction.getRoot().getSpanId().equals(getSpanId())
              ? transaction.getChildren()
              : getDirectChildren();
      for (final Span child : children) {
        final @NotNull Timestamp childStart = child.startTimestamp();
        if (minChildStart == null || childStart.epochNanos() < minChildStart.epochNanos()) {
          minChildStart = childStart;
        }
        final @Nullable Timestamp childEnd = child.endTimestamp();
        if (childEnd != null
            && (maxChildEnd == null || childEnd.epochNanos() > maxChildEnd.epochNanos())) {
          maxChildEnd = childEnd;
        }
      }
      if (options.isTrimStart()
          && minChildStart != null
          && start.epochNanos() < minChildStart.epochNanos()) {
        this.start = minChildStart;
      }
      if (options.isTrimEnd()
          && maxChildEnd != null
          && this.end.epochNanos() > maxChildEnd.epochNanos()) {
        this.end = maxChildEnd;
      }
    }

    if (throwable != null) {
      scopes.setSpanContext(throwable, this, this.transaction.getName());
    }
    if (spanFinishedCallback != null) {
      spanFinishedCallback.execute(this);
    }
    finished = true;
  }

  @Override
  public void setOperation(final @NotNull String operation) {
    this.context.setOperation(operation);
  }

  @Override
  public @NotNull String getOperation() {
    return this.context.getOperation();
  }

  @Override
  public void setDescription(final @Nullable String description) {
    this.context.setDescription(description);
  }

  @Override
  public @Nullable String getDescription() {
    return this.context.getDescription();
  }

  @Override
  public void setStatus(final @Nullable SpanStatus status) {
    this.context.setStatus(status);
  }

  @Override
  public @Nullable SpanStatus getStatus() {
    return this.context.getStatus();
  }

  @Override
  public @NotNull SpanContext getSpanContext() {
    return context;
  }

  @Override
  public void setTag(final @Nullable String key, final @Nullable String value) {
    this.context.setTag(key, value);
  }

  @Override
  public @Nullable String getTag(@Nullable String key) {
    if (key == null) {
      return null;
    }
    return context.getTags().get(key);
  }

  @Override
  public boolean isFinished() {
    return finished;
  }

  public @NotNull Map<String, Object> getData() {
    return data;
  }

  @Override
  public @Nullable Boolean isSampled() {
    return context.getSampled();
  }

  public @Nullable Boolean isProfileSampled() {
    return context.getProfileSampled();
  }

  @Override
  public @Nullable TracesSamplingDecision getSamplingDecision() {
    return context.getSamplingDecision();
  }

  @Override
  public void setThrowable(final @Nullable Throwable throwable) {
    this.throwable = throwable;
  }

  @Override
  public @Nullable Throwable getThrowable() {
    return throwable;
  }

  @NotNull
  public SentryId getTraceId() {
    return context.getTraceId();
  }

  public @NotNull SpanId getSpanId() {
    return context.getSpanId();
  }

  public @Nullable SpanId getParentSpanId() {
    return context.getParentSpanId();
  }

  public Map<String, String> getTags() {
    return context.getTags();
  }

  @Override
  public void setData(final @Nullable String key, final @Nullable Object value) {
    if (key == null) {
      return;
    }
    if (value == null) {
      data.remove(key);
    } else {
      data.put(key, value);
    }
  }

  @Override
  public @Nullable Object getData(final @Nullable String key) {
    if (key == null) {
      return null;
    }
    return data.get(key);
  }

  @Override
  public void setMeasurement(final @NotNull String name, final @NotNull Number value) {
    if (isFinished()) {
      scopes
          .getOptions()
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "The span is already finished. Measurement %s cannot be set",
              name);
      return;
    }
    this.measurements.put(name, new MeasurementValue(value, null));
    // We set the measurement in the transaction, too, but we have to check if this is the root span
    // of the transaction, to avoid an infinite recursion
    if (transaction.getRoot() != this) {
      transaction.setMeasurementFromChild(name, value);
    }
  }

  @Override
  public void setMeasurement(
      final @NotNull String name,
      final @NotNull Number value,
      final @NotNull MeasurementUnit unit) {
    if (isFinished()) {
      scopes
          .getOptions()
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "The span is already finished. Measurement %s cannot be set",
              name);
      return;
    }
    this.measurements.put(name, new MeasurementValue(value, unit.apiName()));
    // We set the measurement in the transaction, too, but we have to check if this is the root span
    // of the transaction, to avoid an infinite recursion
    if (transaction.getRoot() != this) {
      transaction.setMeasurementFromChild(name, value, unit);
    }
  }

  @NotNull
  public Map<String, MeasurementValue> getMeasurements() {
    return measurements;
  }

  @Override
  public boolean updateEndDate(final @NotNull SentryDate date) {
    return updateEndDate(Timestamp.ofEpochNanos(date.nanoTimestamp()));
  }

  boolean updateEndDate(final @NotNull Timestamp date) {
    if (this.end != null) {
      this.end = date;
      return true;
    }
    return false;
  }

  @Override
  public boolean isNoOp() {
    return false;
  }

  @Override
  public void setContext(@Nullable String key, @Nullable Object context) {
    this.contexts.put(key, context);
  }

  @Override
  public @NotNull Contexts getContexts() {
    return contexts;
  }

  void setSpanFinishedCallback(final @Nullable SpanFinishedCallback callback) {
    this.spanFinishedCallback = callback;
  }

  @Nullable
  SpanFinishedCallback getSpanFinishedCallback() {
    return spanFinishedCallback;
  }

  @NotNull
  SpanOptions getOptions() {
    return options;
  }

  @NotNull
  private List<Span> getDirectChildren() {
    final List<Span> children = new ArrayList<>();
    final Iterator<Span> iterator = transaction.getSpans().iterator();

    while (iterator.hasNext()) {
      final Span span = iterator.next();
      if (span.getParentSpanId() != null && span.getParentSpanId().equals(getSpanId())) {
        children.add(span);
      }
    }
    return children;
  }

  @Override
  public @NotNull ISentryLifecycleToken makeCurrent() {
    return NoOpScopesLifecycleToken.getInstance();
  }

  @Override
  public void addFeatureFlag(final @Nullable String flag, final @Nullable Boolean result) {
    context.addFeatureFlag(flag, result);
  }
}
