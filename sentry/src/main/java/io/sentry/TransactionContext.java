package io.sentry;

import io.sentry.protocol.SentryId;
import io.sentry.protocol.TransactionNameSource;
import io.sentry.util.Objects;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TransactionContext extends SpanContext {
  private static final @NotNull String DEFAULT_NAME = "<unlabeled transaction>";
  private static final @NotNull TransactionNameSource DEFAULT_NAME_SOURCE =
      TransactionNameSource.CUSTOM;
  private static final @NotNull String DEFAULT_OPERATION = "default";
  private @NotNull String name;
  private @NotNull TransactionNameSource transactionNameSource;
  private @Nullable TracesSamplingDecision parentSamplingDecision;
  private @Nullable Baggage baggage;
  private @NotNull Instrumenter instrumenter = Instrumenter.SENTRY;

  /**
   * Creates {@link TransactionContext} from sentry-trace header.
   *
   * @param name - the transaction name
   * @param operation - the operation
   * @param sentryTrace - the sentry-trace header
   * @deprecated use {@link Sentry#continueTrace(String, List)} and setters for name and operation
   *     here instead.
   * @return the transaction contexts
   */
  @Deprecated
  public static @NotNull TransactionContext fromSentryTrace(
      final @NotNull String name,
      final @NotNull String operation,
      final @NotNull SentryTraceHeader sentryTrace) {
    @Nullable Boolean parentSampled = sentryTrace.isSampled();
    TracesSamplingDecision samplingDecision =
        parentSampled == null ? null : new TracesSamplingDecision(parentSampled);

    TransactionContext transactionContext =
        new TransactionContext(
            sentryTrace.getTraceId(),
            new SpanId(),
            sentryTrace.getSpanId(),
            samplingDecision,
            null);

    transactionContext.setName(name);
    transactionContext.setTransactionNameSource(TransactionNameSource.CUSTOM);
    transactionContext.setOperation(operation);

    return transactionContext;
  }

  @ApiStatus.Internal
  public static TransactionContext fromPropagationContext(
      final @NotNull PropagationContext propagationContext) {
    @Nullable Boolean parentSampled = propagationContext.isSampled();
    TracesSamplingDecision samplingDecision =
        parentSampled == null ? null : new TracesSamplingDecision(parentSampled);

    @Nullable Baggage baggage = propagationContext.getBaggage();

    if (baggage != null) {
      baggage.freeze();

      Double sampleRate = baggage.getSampleRateDouble();
      Boolean sampled = parentSampled != null ? parentSampled.booleanValue() : false;
      if (sampleRate != null) {
        samplingDecision = new TracesSamplingDecision(sampled, sampleRate);
      } else {
        samplingDecision = new TracesSamplingDecision(sampled);
      }
    }

    return new TransactionContext(
        propagationContext.getTraceId(),
        propagationContext.getSpanId(),
        propagationContext.getParentSpanId(),
        samplingDecision,
        baggage);
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
  }

  @ApiStatus.Internal
  public TransactionContext(
      final @NotNull SentryId traceId,
      final @NotNull SpanId spanId,
      final @Nullable SpanId parentSpanId,
      final @Nullable TracesSamplingDecision parentSamplingDecision,
      final @Nullable Baggage baggage) {
    super(traceId, spanId, DEFAULT_OPERATION, parentSpanId, null);
    this.name = DEFAULT_NAME;
    this.parentSamplingDecision = parentSamplingDecision;
    this.transactionNameSource = DEFAULT_NAME_SOURCE;
    this.baggage = baggage;
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

  public @Nullable Baggage getBaggage() {
    return baggage;
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

  public @NotNull Instrumenter getInstrumenter() {
    return instrumenter;
  }

  public void setInstrumenter(final @NotNull Instrumenter instrumenter) {
    this.instrumenter = instrumenter;
  }

  public void setName(final @NotNull String name) {
    this.name = Objects.requireNonNull(name, "name is required");
  }

  public void setTransactionNameSource(final @NotNull TransactionNameSource transactionNameSource) {
    this.transactionNameSource = transactionNameSource;
  }
}
