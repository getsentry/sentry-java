package io.sentry;

import io.sentry.protocol.SentryId;
import io.sentry.protocol.TransactionNameSource;
import io.sentry.util.CollectionUtils;
import io.sentry.util.Objects;
import io.sentry.util.TracingUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TransactionContext extends SpanContext {
  public static final @NotNull String DEFAULT_TRANSACTION_NAME = "<unlabeled transaction>";
  private static final @NotNull TransactionNameSource DEFAULT_NAME_SOURCE =
      TransactionNameSource.CUSTOM;
  private static final @NotNull String DEFAULT_OPERATION = "default";
  private @NotNull String name;
  private @NotNull TransactionNameSource transactionNameSource;
  private @Nullable TracesSamplingDecision parentSamplingDecision;
  private boolean isForNextAppStart = false;
  private boolean forceNewTrace = false;

  @ApiStatus.Internal
  public static TransactionContext fromPropagationContext(
      final @NotNull PropagationContext propagationContext) {
    final @Nullable Boolean parentSampled = propagationContext.isSampled();
    final @NotNull Baggage baggage = propagationContext.getBaggage();
    final @Nullable Double sampleRate = baggage.getSampleRate();
    final @Nullable TracesSamplingDecision samplingDecision =
        parentSampled == null
            ? null
            : new TracesSamplingDecision(
                parentSampled, sampleRate, propagationContext.getSampleRand());

    return new TransactionContext(
        propagationContext.getTraceId(),
        propagationContext.getSpanId(),
        propagationContext.getParentSpanId(),
        samplingDecision,
        baggage);
  }

  @ApiStatus.Internal
  static @NotNull TransactionContext fromPropagationContextAsRoot(
      final @NotNull PropagationContext propagationContext,
      final @NotNull TransactionContext transactionContext) {
    final @NotNull Baggage baggage =
        Baggage.copyWithOverrides(
            propagationContext.getBaggage(),
            propagationContext.getTraceId(),
            propagationContext.getSampleRand());

    final @NotNull TransactionContext sessionContext =
        new TransactionContext(propagationContext.getTraceId(), new SpanId(), null, null, baggage);
    sessionContext.setName(transactionContext.getName());
    sessionContext.setTransactionNameSource(transactionContext.getTransactionNameSource());
    sessionContext.setOperation(transactionContext.getOperation());
    sessionContext.setDescription(transactionContext.getDescription());
    sessionContext.setStatus(transactionContext.getStatus());
    sessionContext.setOrigin(transactionContext.getOrigin());
    sessionContext.setInstrumenter(transactionContext.getInstrumenter());
    sessionContext.setSamplingDecision(transactionContext.getSamplingDecision());
    sessionContext.setForNextAppStart(transactionContext.isForNextAppStart());
    sessionContext.setProfilerId(transactionContext.getProfilerId());
    final @Nullable java.util.Map<String, @NotNull String> copiedTags =
        CollectionUtils.newConcurrentHashMap(transactionContext.tags);
    if (copiedTags != null) {
      sessionContext.tags = copiedTags;
    }
    final @Nullable java.util.Map<String, @NotNull Object> copiedData =
        CollectionUtils.newConcurrentHashMap(transactionContext.data);
    if (copiedData != null) {
      sessionContext.data = copiedData;
    }
    sessionContext.forceNewTrace = transactionContext.forceNewTrace;
    return sessionContext;
  }

  public TransactionContext(final @NotNull String name, final @NotNull String operation) {
    this(name, operation, null);
  }

  @ApiStatus.Internal
  public TransactionContext(
      final @NotNull String name,
      final @NotNull TransactionNameSource transactionNameSource,
      final @NotNull String operation) {
    this(name, transactionNameSource, operation, null);
  }

  /**
   * Creates {@link TransactionContext} with explicit sampling decision.
   *
   * @param name - transaction name
   * @param operation - operation
   * @param samplingDecision - sampling decision
   */
  public TransactionContext(
      final @NotNull String name,
      final @NotNull String operation,
      final @Nullable TracesSamplingDecision samplingDecision) {
    this(name, TransactionNameSource.CUSTOM, operation, samplingDecision);
  }

  /**
   * Creates {@link TransactionContext} with explicit sampling decision and name source.
   *
   * @param name - transaction name
   * @param operation - operation
   * @param samplingDecision - sampling decision
   */
  @ApiStatus.Internal
  public TransactionContext(
      final @NotNull String name,
      final @NotNull TransactionNameSource transactionNameSource,
      final @NotNull String operation,
      final @Nullable TracesSamplingDecision samplingDecision) {
    super(operation);
    this.name = Objects.requireNonNull(name, "name is required");
    this.transactionNameSource = transactionNameSource;
    this.setSamplingDecision(samplingDecision);
    this.baggage = TracingUtils.ensureBaggage(null, samplingDecision);
  }

  @ApiStatus.Internal
  public TransactionContext(
      final @NotNull SentryId traceId,
      final @NotNull SpanId spanId,
      final @Nullable SpanId parentSpanId,
      final @Nullable TracesSamplingDecision parentSamplingDecision,
      final @Nullable Baggage baggage) {
    super(traceId, spanId, DEFAULT_OPERATION, parentSpanId, null);
    this.name = DEFAULT_TRANSACTION_NAME;
    this.parentSamplingDecision = parentSamplingDecision;
    this.transactionNameSource = DEFAULT_NAME_SOURCE;
    this.baggage = TracingUtils.ensureBaggage(baggage, parentSamplingDecision);
  }

  public @NotNull String getName() {
    return name;
  }

  public @Nullable Boolean getParentSampled() {
    if (parentSamplingDecision == null) {
      return null;
    }

    return parentSamplingDecision.getSampled();
  }

  public @Nullable TracesSamplingDecision getParentSamplingDecision() {
    return parentSamplingDecision;
  }

  public void setParentSampled(final @Nullable Boolean parentSampled) {
    if (parentSampled == null) {
      this.parentSamplingDecision = null;
    } else {
      this.parentSamplingDecision = new TracesSamplingDecision(parentSampled);
    }
  }

  public void setParentSampled(
      final @Nullable Boolean parentSampled, final @Nullable Boolean parentProfileSampled) {
    if (parentSampled == null) {
      this.parentSamplingDecision = null;
    } else if (parentProfileSampled == null) {
      this.parentSamplingDecision = new TracesSamplingDecision(parentSampled);
    } else {
      this.parentSamplingDecision =
          new TracesSamplingDecision(parentSampled, null, parentProfileSampled, null);
    }
  }

  public @NotNull TransactionNameSource getTransactionNameSource() {
    return transactionNameSource;
  }

  public void setName(final @NotNull String name) {
    this.name = Objects.requireNonNull(name, "name is required");
  }

  public void setTransactionNameSource(final @NotNull TransactionNameSource transactionNameSource) {
    this.transactionNameSource = transactionNameSource;
  }

  /**
   * Forces this transaction to start a new trace when session trace lifecycle is enabled.
   * Explicitly continued traces with a parent span are still preserved.
   *
   * @return true if this transaction should not reuse the session propagation context.
   */
  @ApiStatus.Experimental
  public boolean isForceNewTrace() {
    return forceNewTrace;
  }

  /**
   * Forces this transaction to start a new trace when session trace lifecycle is enabled.
   * Explicitly continued traces with a parent span are still preserved.
   *
   * @param forceNewTrace true to keep this transaction on a new trace.
   */
  @ApiStatus.Experimental
  public void setForceNewTrace(final boolean forceNewTrace) {
    this.forceNewTrace = forceNewTrace;
  }

  @ApiStatus.Internal
  public void setForNextAppStart(final boolean forNextAppStart) {
    isForNextAppStart = forNextAppStart;
  }

  /**
   * Whether this {@link TransactionContext} evaluates for the next app start. If this is true, it
   * gets called only once when the SDK initializes. This is set only if {@link
   * SentryOptions#isEnableAppStartProfiling()} is true.
   *
   * @return True if this {@link TransactionContext} will be used for the next app start.
   */
  public boolean isForNextAppStart() {
    return isForNextAppStart;
  }
}
