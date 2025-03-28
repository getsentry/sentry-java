package io.sentry;

import io.sentry.protocol.Contexts;
import io.sentry.protocol.MeasurementValue;
import io.sentry.protocol.SentryId;
import io.sentry.util.Objects;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class Span implements ISpan {

  /** The moment in time when span was started. */
  private @NotNull SentryDate startTimestamp;

  /** The moment in time when span has ended. */
  private @Nullable SentryDate timestamp;

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

  private final @NotNull List<SpanLink> links = new CopyOnWriteArrayList<>();
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
    final @Nullable SentryDate startTimestamp = options.getStartTimestamp();
    if (startTimestamp != null) {
      this.startTimestamp = startTimestamp;
    } else {
      this.startTimestamp = scopes.getOptions().getDateProvider().now();
    }
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
    final @Nullable SentryDate startTimestamp = options.getStartTimestamp();
    if (startTimestamp != null) {
      this.startTimestamp = startTimestamp;
    } else {
      this.startTimestamp = scopes.getOptions().getDateProvider().now();
    }
    this.options = options;
  }

  @Override
  public @NotNull SentryDate getStartDate() {
    return startTimestamp;
  }

  @Override
  public @Nullable SentryDate getFinishDate() {
    return timestamp;
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
    finish(status, scopes.getOptions().getDateProvider().now());
  }

  /**
   * Used to finish unfinished spans by {@link SentryTracer}.
   *
   * @param status - status to finish span with
   * @param timestamp - the root span timestamp.
   */
  @Override
  public void finish(final @Nullable SpanStatus status, final @Nullable SentryDate timestamp) {
    // the span can be finished only once
    if (finished || !isFinishing.compareAndSet(false, true)) {
      return;
    }

    this.context.setStatus(status);
    this.timestamp = timestamp == null ? scopes.getOptions().getDateProvider().now() : timestamp;
    if (options.isTrimStart() || options.isTrimEnd()) {
      @Nullable SentryDate minChildStart = null;
      @Nullable SentryDate maxChildEnd = null;

      // The root span should be trimmed based on all children, but the other spans, like the
      // jetpack composition should be trimmed based on its direct children only
      final @NotNull List<Span> children =
          transaction.getRoot().getSpanId().equals(getSpanId())
              ? transaction.getChildren()
              : getDirectChildren();
      for (final Span child : children) {
        if (minChildStart == null || child.getStartDate().isBefore(minChildStart)) {
          minChildStart = child.getStartDate();
        }
        if (maxChildEnd == null
            || (child.getFinishDate() != null && child.getFinishDate().isAfter(maxChildEnd))) {
          maxChildEnd = child.getFinishDate();
        }
      }
      if (options.isTrimStart()
          && minChildStart != null
          && startTimestamp.isBefore(minChildStart)) {
        updateStartDate(minChildStart);
      }
      if (options.isTrimEnd()
          && maxChildEnd != null
          && (this.timestamp == null || this.timestamp.isAfter(maxChildEnd))) {
        updateEndDate(maxChildEnd);
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
    if (this.timestamp != null) {
      this.timestamp = date;
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

  private void updateStartDate(@NotNull SentryDate date) {
    this.startTimestamp = date;
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
  public void addLink(@NotNull SpanLink spanLink) {
    links.add(spanLink);
  }

  @Override public @NotNull List<SpanLink> getLinks() {
    return links;
  }
}
